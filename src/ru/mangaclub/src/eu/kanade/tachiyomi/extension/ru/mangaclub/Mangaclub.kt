package eu.kanade.tachiyomi.extension.ru.mangaclub

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mangaclub : ParsedHttpSource() {

    // Info

    override val name: String = "MangaClub"
    override val baseUrl: String = "https://mangaclub.ru"
    override val lang: String = "ru"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/f/order_by=news_read/order=desc/page/$page/", headers)

    override fun popularMangaNextPageSelector(): String? = "a i.glyphicon-chevron-right"
    override fun popularMangaSelector(): String = "div#dle-content div.short-story"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select(".title > a").apply {
            title = this.text().substringBefore("/").trim()
            setUrlWithoutDomain(this.attr("abs:href"))
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("do", "search")
                .add("subaction", "search")
                .add("search_start", page.toString())
                .add("full_search", "0")
                .add("result_from", ((page - 1) * 8 + 1).toString())
                .add("story", query)
                .build()
            val searchHeaders =
                headers.newBuilder().add("Content-Type", "application/x-www-form-urlencoded")
                    .build()
            return POST("$baseUrl/index.php?do=search", searchHeaders, formBody)
        } else {
            val uri = Uri.parse(baseUrl).buildUpon()
                .appendPath("f")
            for (filter in filters) {
                when (filter) {
                    is Categories -> if (filter.state > 0) uri.appendEncodedPath("cat=${categoriesArray[filter.state].second}")
                    is Status -> if (filter.values[filter.state].isNotEmpty()) uri.appendEncodedPath(
                        "status_translation=${filter.values[filter.state]}"
                    )
                    is Tag -> if (filter.values[filter.state].isNotEmpty()) uri.appendEncodedPath("m.tags=${filter.values[filter.state]}")
                    is Sort ->
                        if (filter.state != null) {
                            if (filter.state!!.ascending) {
                                uri.appendEncodedPath("order_by=${sortables[filter.state!!.index].second}")
                                    .appendEncodedPath("order=asc")
                            } else {
                                uri.appendEncodedPath("order_by=${sortables[filter.state!!.index].second}")
                                    .appendEncodedPath("order=desc")
                            }
                        }
                }
            }
            uri.appendPath("page")
                .appendPath(page.toString())
            return GET(uri.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("img[title].img-responsive").attr("abs:src")
        title = document.select(".title").text().substringBefore("/").trim()
        author = document.select("a[href*=author]").text().trim()
        artist = author
        status = when (document.select("a[href*=status_translation]")?.first()?.text()) {
            "Продолжается" -> SManga.ONGOING
            "Завершен" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.select("div.description_manga").text().trim()
        genre = document.select("div.more-info a[href*=tags]").joinToString(", ") { it.text() }
    }

    // Chapters
    override fun chapterListSelector(): String = ".chapter-main"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select(".chapter-main .chapter-namber a").text().trim()
        chapter_number = name.substringAfter("Глава").replace(",", ".").trim().toFloat()
        setUrlWithoutDomain(element.select(".chapter-main .chapter-namber a").attr("abs:href"))
        date_upload = parseDate(element.select(".chapter-date").text().trim())
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select(".manga-lines-page a").forEach {
            add(Page(it.attr("data-p").toInt(), "", it.attr("abs:data-i")))
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw Exception("imageUrlParse Not Used")

    // Filters
    private class Categories(values: Array<Pair<String, String>>) :
        Filter.Select<String>("Категории", values.map { it.first }.toTypedArray())

    private class Tag(values: Array<String>) : Filter.Select<String>("Жанр", values)
    private class Sort(values: List<Pair<String, String>>) : Filter.Sort(
        "Сортировать результат поиска", values.map { it.first }.toTypedArray(),
        Selection(2, false)
    )

    private class Status : Filter.Select<String>(
        "Статус",
        arrayOf("", "Завершен", "Продолжается", "Заморожено/Заброшено")
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Filters are ignored if using text search."),
        Categories(categoriesArray),
        Status(),
        Tag(tag),
        Sort(sortables)
    )

    private val categoriesArray = arrayOf(
        Pair("", ""),
        Pair("Манга", "1"),
        Pair("Манхва", "2"),
        Pair("Маньхуа", "3"),
        Pair("Веб-манхва", "6")
    )

    private val tag = arrayOf(
        "",
        "Боевые искусства",
        "Боевик",
        "Вампиры",
        "Гарем",
        "Гендерная интрига",
        "Героическое фэнтези",
        "Додзинси",
        "Дзёсэй",
        "Драма",
        "Детектив",
        "Игра",
        "История",
        "Киберпанк",
        "Комедия",
        "Мистика",
        "Меха",
        "Махо-сёдзё",
        "Научная фантастика",
        "Повседневность",
        "Приключения",
        "Психология",
        "Романтика",
        "Самурайский боевик",
        "Сверхъестественное",
        "Сёдзё",
        "Сёдзё для взрослых",
        "Сёдзё-ай",
        "Сёнэн",
        "Сёнэн-ай",
        "Спокон",
        "Сэйнэн",
        "Спорт",
        "Трагедия",
        "Триллер",
        "Ужасы",
        "Фантастика",
        "Фэнтези",
        "Школа",
        "Эротика",
        "Этти",
        "Юри",
        "Яой"
    )

    private val sortables = listOf(
        Pair("По заголовку", "title"),
        Pair("По количеству комментариев", "comm_num"),
        Pair("По количеству просмотров", "news_read"),
        Pair("По имени автора", "autor"),
        Pair("По рейтингу", "rating")
    )
}
