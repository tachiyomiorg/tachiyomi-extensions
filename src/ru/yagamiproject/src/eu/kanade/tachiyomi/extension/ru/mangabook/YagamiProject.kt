package eu.kanade.tachiyomi.extension.ru.yagamiproject

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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class YagamiProject : ParsedHttpSource() {
    // Info
    override val name = "YagamiProject"
    override val baseUrl = "https://read.yagami.me"
    override val lang = "ru"
    override val supportsLatest = true
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/list-new/$page", headers)
    override fun popularMangaNextPageSelector() = ".panel_nav .button a"
    override fun popularMangaSelector() = ".list .group"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select(".title a").first().let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.attr("title").split(" / ").sorted().first()
            }
            thumbnail_url = element.select(".cover_mini > img").attr("src")
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/$page", headers)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/dosearch?query=$query&page=$page"
        } else {
            val url = "$baseUrl/filterList?page=$page&ftype[]=0&status[]=0".toHttpUrlOrNull()!!.newBuilder()
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is OrderBy -> {
                        val ord = arrayOf("views", "rate", "name", "created_at")[filter.state]
                        url.addQueryParameter("sortBy", ord)
                    }
                    is CategoryList -> {
                        if (filter.state > 0) {
                            val CatQ = getCategoryList()[filter.state].query
                            url.addQueryParameter("cat", CatQ)
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
                title = it.select("h4 strong").text().split(" / ").sorted().first()
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
        val infoElement = document.select(".large.comic .info").first()
        val manga = SManga.create()
        val titlestr = document.select("title").text().substringBefore(" :: Yagami").split(" :: ").sorted()
        manga.title = titlestr.first()
        manga.thumbnail_url = document.select(".cover img").first().attr("src")
        manga.author = infoElement.select("li:contains(Автор)").text().substringAfter("Автор(ы): ")
        manga.status = when (infoElement.select("li:contains(Статус перевода) span").text()) {
            "онгоинг" -> SManga.ONGOING
            "завершён" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.genre = infoElement.select("li:contains(Жанры)").text().substringAfter("Жанры: ")
        manga.description = titlestr.last() + "\n" + infoElement.select("li:contains(Описание)").text().substringAfter("Описание: ")
        return manga
    }

    // Chapters
    override fun chapterListSelector(): String = ".list .element"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapter = element.select(".title a")
        val chapterScan_Date = element.select(".meta_r")
        name = chapter.attr("title")
        chapter_number = name.substringAfter("Глава ").substringBefore(":").toFloat()
        setUrlWithoutDomain(chapter.attr("href"))
        date_upload = parseDate(chapterScan_Date.text().substringAfter(", "))
        scanlator = if (chapterScan_Date.select("a").isNotEmpty()) {
            chapterScan_Date.select("a").map { it.text() }.joinToString(" / ")
        } else null
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0
    }
    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".reader-images img.img-responsive:not(.scan-page)").mapIndexed { i, img ->
            Page(i, "", img.attr("data-src").trim())
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("imageUrlParse Not Used")

    // Filters
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class FormatList(formas: List<CheckFilter>) : Filter.Group<CheckFilter>("Тип", formas)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(categoriesName),
        StatusList(getStatusList()),
        FormatList(getFormatList())
    )

    private class OrderBy : Filter.Select<String>(
        "Сортировка",
        arrayOf("По популярности", "По рейтингу", "По алфавиту", "По дате выхода")
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

    private class CategoryList(categories: Array<String>) : Filter.Select<String>("Категории", categories)
    private data class CatUnit(val name: String, val query: String)
    private val categoriesName = getCategoryList().map {
        it.name
    }.toTypedArray()

    private fun getCategoryList() = listOf(
        CatUnit("Без категории", "not"),
        CatUnit("16+", "16+"),
        CatUnit("Арт", "art"),
        CatUnit("Бара", "bara"),
        CatUnit("Боевик", "action"),
        CatUnit("Боевые искусства", "combatskill"),
        CatUnit("В цвете", "vcvete"),
        CatUnit("Вампиры", "vampaires"),
        CatUnit("Веб", "web"),
        CatUnit("Вестерн", "western"),
        CatUnit("Гарем", "harem"),
        CatUnit("Гендерная интрига", "genderintrigue"),
        CatUnit("Героическое фэнтези", "heroic_fantasy"),
        CatUnit("Детектив", "detective"),
        CatUnit("Дзёсэй", "josei"),
        CatUnit("Додзинси", "doujinshi"),
        CatUnit("Драма", "drama"),
        CatUnit("Ёнкома", "yonkoma"),
        CatUnit("Есси", "18+"),
        CatUnit("Зомби", "zombie"),
        CatUnit("Игра", "games"),
        CatUnit("Инцест", "incest"),
        CatUnit("Исекай", "isekai"),
        CatUnit("Искусство", "iskusstvo"),
        CatUnit("Исторический", "historical"),
        CatUnit("Киберпанк", "cyberpunk"),
        CatUnit("Кодомо", "kodomo"),
        CatUnit("Комедия", "comedy"),
        CatUnit("Культовое", "iconic"),
        CatUnit("литРПГ", "litrpg"),
        CatUnit("Любовь", "love"),
        CatUnit("Махо-сёдзё", "maho-shojo"),
        CatUnit("Меха", "robots"),
        CatUnit("Мистика", "mystery"),
        CatUnit("Мужская беременность", "male-pregnancy"),
        CatUnit("Музыка", "music"),
        CatUnit("Научная фантастика", "sciencefiction"),
        CatUnit("Новинки", "new"),
        CatUnit("Омегаверс", "omegavers"),
        CatUnit("Перерождение", "newlife"),
        CatUnit("Повседневность", "humdrum"),
        CatUnit("Постапокалиптика", "postapocalyptic"),
        CatUnit("Приключения", "adventure"),
        CatUnit("Психология", "psychology"),
        CatUnit("Романтика", "romance"),
        CatUnit("Самураи", "samurai"),
        CatUnit("Сборник", "compilation"),
        CatUnit("Сверхъестественное", "supernatural"),
        CatUnit("Сёдзё", "shojo"),
        CatUnit("Сёдзё-ай", "maho-shojo"),
        CatUnit("Сёнэн", "senen"),
        CatUnit("Сёнэн-ай", "shonen-ai"),
        CatUnit("Сетакон", "setakon"),
        CatUnit("Сингл", "singl"),
        CatUnit("Сказка", "fable"),
        CatUnit("Сорс", "bdsm"),
        CatUnit("Спорт", "sport"),
        CatUnit("Супергерои", "superheroes"),
        CatUnit("Сэйнэн", "seinen"),
        CatUnit("Танцы", "dancing"),
        CatUnit("Трагедия", "tragedy"),
        CatUnit("Триллер", "thriller"),
        CatUnit("Ужасы", "horror"),
        CatUnit("Фантастика", "fantastic"),
        CatUnit("Фурри", "furri"),
        CatUnit("Фэнтези", "fantasy"),
        CatUnit("Школа", "school"),
        CatUnit("Эротика", "erotica"),
        CatUnit("Этти", "etty"),
        CatUnit("Юмор", "humor"),
        CatUnit("Юри", "yuri"),
        CatUnit("Яой", "yaoi")
    )
}
