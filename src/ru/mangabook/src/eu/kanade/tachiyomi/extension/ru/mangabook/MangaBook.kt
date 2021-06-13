package eu.kanade.tachiyomi.extension.ru.mangabook

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBook : ParsedHttpSource() {
    // Info
    override val name = "MangaBook"
    override val baseUrl = "https://mangabook.org"
    override val lang = "ru"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Accept", "image/webp,*/*;q=0.8")
        .add("Referer", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/filterList?page=$page&ftype[]=0&status[]=0&sortBy=rate", headers)
    override fun popularMangaNextPageSelector() = "a.page-link[rel=next]"
    override fun popularMangaSelector() = "article.short .short-in"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select(".sh-desc a").first().let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.select("div.sh-title").text().split(" / ").last()
            }
            thumbnail_url = element.select(".short-poster.img-box > img").attr("src")
        }
    }
    // Latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/dosearch?&query=$query"
        } else {
            val url = "$baseUrl/filterList?page=$page&ftype[]=0&status[]=0&sortBy=rate".toHttpUrlOrNull()!!.newBuilder()
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is OrderBy -> {
                        val ord = arrayOf("rate", "name", "views", "created_at")[filter.state]
                        url.addQueryParameter("sortBy", "$ord")
                    }
                    is CategoryList -> filter.state.forEach { category ->
                        if (category.state) {
                            url.addQueryParameter("cat", category.id)
                        }
                    }
                    is StatusList -> filter.state.forEach { status ->
                        if (status.state) {
                            url.addQueryParameter("status[]", status.id)
                        }
                    }
                    is FormatList -> filter.state.forEach { forma ->
                        if (forma.state) {
                            url.addQueryParameter("ftype[]", forma.id)
                        }
                    }
                }
            }
            return GET(url.toString(), headers)
        }
        return GET(url, headers)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select(".flist.row a").first().let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.select("h4").text().split(" / ").last()
            }
            thumbnail_url = element.select(".sposter img.img-responsive").attr("src")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.toString().contains("dosearch")) {
            return popularMangaParse(response)
        }
        val document = response.asJsoup()
        val mangas = document.select(".manga-list li:not(.vis )").map { element ->
            searchMangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("article.full .fmid").first()
        val manga = SManga.create()
        manga.title = document.select(".fheader h1").text().split(" / ").last()
        manga.thumbnail_url = infoElement.select("img.img-responsive").first().attr("src")
        manga.author = infoElement.select(".vis:contains(Автор) > a").text()
        manga.artist = infoElement.select(".vis:contains(Художник) > a").text()
        manga.status = if (document.select(".fheader h2").text() == "Чтение заблокировано") {
            SManga.LICENSED
        } else
            when (infoElement.select(".vis:contains(Статус) span.label").text()) {
                "Сейчас издаётся" -> SManga.ONGOING
                "Изданное" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

        val rawCategory = infoElement.select(".vis:contains(Жанр (вид)) span.label").text()
        val category = when {
            rawCategory == "Веб-Манхва" -> "Манхва"
            rawCategory.isNotBlank() -> rawCategory
            else -> "Манхва"
        }
        manga.genre = infoElement.select(".vis:contains(Категории) > a").map { it.text() }.plusElement(category).joinToString { it.trim() }
        manga.description = infoElement.select(".fdesc.slice-this").text()
        return manga
    }

    // Chapters
    override fun chapterListSelector(): String = ".chapters li:not(.volume )"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.select("h5 a")
        name = link.text()
        chapter_number = name.substringAfter("Глава №").substringBefore(":").toFloat()
        setUrlWithoutDomain(link.attr("href") + "/1")
        date_upload = parseDate(element.select(".date-chapter-title-rtl").text().trim())
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0
    }
    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".reader-images img.img-responsive").mapIndexed { i, img ->
            Page(i, "", img.attr("data-src").trim())
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("imageUrlParse Not Used")

    // Filters
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class FormatList(formas: List<CheckFilter>) : Filter.Group<CheckFilter>("Тип", formas)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)
    private class CategoryList(categories: List<CheckFilter>) : Filter.Group<CheckFilter>("Категории", categories)
    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        StatusList(getStatusList()),
        FormatList(getFormatList())
    )

    private class OrderBy : Filter.Select<String>(
        "Сортировка",
        arrayOf("По рейтингу", "По алфавиту", "По популярности", "По дате выхода")
    )
    private fun getFormatList() = listOf(
        CheckFilter("Манга", "1"),
        CheckFilter("Манхва", "2"),
        CheckFilter("Веб Манхва", "4"),
        CheckFilter("Маньхуа", "3")
    )

    private fun getStatusList() = listOf(
        CheckFilter("Сейчас издаётся", "1"),
        CheckFilter("Анонсировано", "3"),
        CheckFilter("Изданное", "2")
    )

    private fun getCategoryList() = listOf(
        CheckFilter("16+", "16+"),
        CheckFilter("Арт", "art"),
        CheckFilter("Бара", "bara"),
        CheckFilter("Боевик", "action"),
        CheckFilter("Боевые искусства", "combatskill"),
        CheckFilter("В цвете", "vcvete"),
        CheckFilter("Вампиры", "vampaires"),
        CheckFilter("Веб", "web"),
        CheckFilter("Вестерн", "western"),
        CheckFilter("Гарем", "harem"),
        CheckFilter("Гендерная интрига", "genderintrigue"),
        CheckFilter("Героическое фэнтези", "heroic_fantasy"),
        CheckFilter("Детектив", "detective"),
        CheckFilter("Дзёсэй", "josei"),
        CheckFilter("Додзинси", "doujinshi"),
        CheckFilter("Драма", "drama"),
        CheckFilter("Ёнкома", "yonkoma"),
        CheckFilter("Есси", "18+"),
        CheckFilter("Зомби", "zombie"),
        CheckFilter("Игра", "games"),
        CheckFilter("Инцест", "incest"),
        CheckFilter("Исекай", "isekai"),
        CheckFilter("Искусство", "iskusstvo"),
        CheckFilter("Исторический", "historical"),
        CheckFilter("Киберпанк", "cyberpunk"),
        CheckFilter("Кодомо", "kodomo"),
        CheckFilter("Комедия", "comedy"),
        CheckFilter("Культовое", "iconic"),
        CheckFilter("литРПГ", "litrpg"),
        CheckFilter("Любовь", "love"),
        CheckFilter("Махо-сёдзё", "maho-shojo"),
        CheckFilter("Меха", "robots"),
        CheckFilter("Мистика", "mystery"),
        CheckFilter("Мужская беременность", "male-pregnancy"),
        CheckFilter("Музыка", "music"),
        CheckFilter("Научная фантастика", "sciencefiction"),
        CheckFilter("Новинки", "new"),
        CheckFilter("Омегаверс", "omegavers"),
        CheckFilter("Перерождение", "newlife"),
        CheckFilter("Повседневность", "humdrum"),
        CheckFilter("Постапокалиптика", "postapocalyptic"),
        CheckFilter("Приключения", "adventure"),
        CheckFilter("Психология", "psychology"),
        CheckFilter("Романтика", "romance"),
        CheckFilter("Самураи", "samurai"),
        CheckFilter("Сборник", "compilation"),
        CheckFilter("Сверхъестественное", "supernatural"),
        CheckFilter("Сёдзё", "shojo"),
        CheckFilter("Сёдзё-ай", "maho-shojo"),
        CheckFilter("Сёнэн", "senen"),
        CheckFilter("Сёнэн-ай", "shonen-ai"),
        CheckFilter("Сетакон", "setakon"),
        CheckFilter("Сингл", "singl"),
        CheckFilter("Сказка", "fable"),
        CheckFilter("Сорс", "bdsm"),
        CheckFilter("Спорт", "sport"),
        CheckFilter("Супергерои", "superheroes"),
        CheckFilter("Сэйнэн", "seinen"),
        CheckFilter("Танцы", "dancing"),
        CheckFilter("Трагедия", "tragedy"),
        CheckFilter("Триллер", "thriller"),
        CheckFilter("Ужасы", "horror"),
        CheckFilter("Фантастика", "fantastic"),
        CheckFilter("Фурри", "furri"),
        CheckFilter("Фэнтези", "fantasy"),
        CheckFilter("Школа", "school"),
        CheckFilter("Эротика", "erotica"),
        CheckFilter("Этти", "etty"),
        CheckFilter("Юмор", "humor"),
        CheckFilter("Юри", "yuri"),
        CheckFilter("Яой", "yaoi")
    )
}
