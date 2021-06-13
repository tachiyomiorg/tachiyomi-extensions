package eu.kanade.tachiyomi.extension.ru.mangabook

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
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
            var ret = String()
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        ret = "$baseUrl/genre/${filter.values[filter.state].id}/page/$page"
                    }
                }
            }
            ret
        }
        return GET(url, headers)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = ".manga-list li:not(.vis )"
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select(".flist.row a").first().let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.select("h4").text().split(" / ").last()
            }
            thumbnail_url = element.select(".sposter img.img-responsive").attr("src")
        }
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("article.full .fmid").first()
        val manga = SManga.create()
        manga.title = document.select(".fheader h1").text().split(" / ").last()
        manga.thumbnail_url = infoElement.select("img.img-responsive").first().attr("src")
        manga.author = infoElement.select(".vis:contains(Автор) > a").text()
        manga.artist = infoElement.select(".vis:contains(Художник) > a").text()
        manga.status = when (infoElement.select(".vis:contains(Статус) span.label").text()) {
            "Сейчас издаётся" -> SManga.ONGOING
            "Изданное" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val rawCategory = infoElement.select(".vis:contains(Жанр (вид)) span.label").text()
        val category = when {
            rawCategory == "Веб-Манхва" -> "Манхва"
            else -> rawCategory
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
    private class Genre(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }

    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Genres", genres, 0)

    override fun getFilterList() = FilterList(
        GenreList(getGenreList())
    )

    private fun getGenreList() = arrayOf(
        Genre("Все", "all"),
        Genre("Боевик", "boevik"),
        Genre("Боевые искусства", "boevye_iskusstva"),
        Genre("Вампиры", "vampiry"),
        Genre("Гарем", "garem"),
        Genre("Гендерная интрига", "gendernaya_intriga"),
        Genre("Героическое фэнтези", "geroicheskoe_fehntezi"),
        Genre("Детектив", "detektiv"),
        Genre("Дзёсэй", "dzyosehj"),
        Genre("Додзинси", "dodzinsi"),
        Genre("Драма", "drama"),
        Genre("Игра", "igra"),
        Genre("История", "istoriya"),
        Genre("Меха", "mekha"),
        Genre("Мистика", "mistika"),
        Genre("Научная фантастика", "nauchnaya_fantastika"),
        Genre("Повседневность", "povsednevnost"),
        Genre("Постапокалиптика", "postapokaliptika"),
        Genre("Приключения", "priklyucheniya"),
        Genre("Психология", "psihologiya"),
        Genre("Романтика", "romantika"),
        Genre("Самурайский боевик", "samurajskij_boevik"),
        Genre("Сверхъестественное", "sverhestestvennoe"),
        Genre("Сёдзё", "syodzyo"),
        Genre("Сёдзё-ай", "syodzyo-aj"),
        Genre("Сёнэн", "syonen"),
        Genre("Спорт", "sport"),
        Genre("Сэйнэн", "sejnen"),
        Genre("Трагедия", "tragediya"),
        Genre("Триллер", "triller"),
        Genre("Ужасы", "uzhasy"),
        Genre("Фантастика", "fantastika"),
        Genre("Фэнтези", "fentezi"),
        Genre("Школа", "shkola"),
        Genre("Этти", "etti"),
        Genre("Юри", "yuri"),
        Genre("Военный", "voennyj"),
        Genre("Жосей", "zhosej"),
        Genre("Магия", "magiya"),
        Genre("Полиция", "policiya"),
        Genre("Смена пола", "smena-pola"),
        Genre("Супер сила", "super-sila"),
        Genre("Эччи", "echchi"),
        Genre("Яой", "yaoj"),
        Genre("Сёнэн-ай", "syonen-aj")
    )
}
