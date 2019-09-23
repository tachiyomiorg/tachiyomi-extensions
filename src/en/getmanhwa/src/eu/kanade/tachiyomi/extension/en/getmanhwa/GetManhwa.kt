package eu.kanade.tachiyomi.extension.en.getmanhwa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class GetManhwa : ParsedHttpSource() {

    override val name = "GetManhwa"

    override val baseUrl = "https://getmanhwa.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) {
            GET(baseUrl, headers)
        } else {
            GET("$baseUrl/home/page-$page/", headers)
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseMangaDocument(response.asJsoup())
    }

    private fun parseMangaDocument(document: Document): MangasPage {
        val mangas = mutableListOf<SManga>()

        document.select(popularMangaSelector()).map{ mangas.add(popularMangaFromElement(it)) }

        return MangasPage(mangas, document.select(popularMangaNextPageSelector()).isNotEmpty())
    }

    override fun popularMangaSelector() = "section:has(h1.elementor-heading-title) div.elementor-widget-wrap section:has(img)"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("div.elementor-flip-box__layer__description").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "i.fa-angle-right"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "div.elementor-widget-wrap:contains(recent episodes) [data-column-clickable]"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("data-column-clickable").substringBeforeLast("chapter"))
        manga.title = element.select("div.elementor-flip-box__layer__description").first().text()

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a.next:has(i.fa-angle-right)"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            GET("$baseUrl/?s=$query", headers)
        } else {
            lateinit var genre: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        genre = filter.toUriPart()
                    }
                }
            }
            GET("$baseUrl/genre-$genre/",headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return if (document.location().contains("?s=")) {
            // for search by query
            val mangas = mutableListOf<SManga>()
            document.select(searchMangaSelector()).map{ e -> mangas.add(searchMangaFromElement(e)) }
            MangasPage(mangas, document.select(searchMangaNextPageSelector()).isNotEmpty())
        } else {
            // for search by genre
            parseMangaDocument(document)
        }
    }

    override fun searchMangaSelector() = "div.search-entry-inner"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("h2 a").let{
            manga.title = it.text()
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun searchMangaNextPageSelector() = "not using this"

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.elementor-row:contains(creator)")

        val manga = SManga.create()
        manga.title = infoElement.select("h2:not(:has(a))").first().text()
        manga.author = infoElement.select("div.elementor-text-editor:contains(creator) p").first().text().substringAfter("Creator: ")
        val status = infoElement.select("span.elementor-button-text").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("div.elementor-clearfix a").text().replace(" ", ", ")
        manga.description = infoElement.select("div.elementor-clearfix:not(:contains(creator))").text()
        manga.thumbnail_url = infoElement.select("img").attr("abs:src")
        return manga
    }

    private fun parseStatus(status: String?) = when (status?.toLowerCase()) {
        null -> SManga.UNKNOWN
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" -> SManga.ONGOING
        "end" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    /** Although getmanhwa isn't madara-based per se, there's a possibility of loading a page bassed
    * off of madara, hence two selectors and an if-then for chapterFromElement
    */
    override fun chapterListSelector() = "[data-column-clickable]:contains(chapter), li.wp-manga-chapter"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        if (element.hasAttr("data-id")) {
            chapter.setUrlWithoutDomain(element.attr("data-column-clickable"))
            chapter.name = element.text().substringBeforeLast(" – ")
            chapter.date_upload = parseChapterDate(element.select("div.elementor-clearfix").text().substringAfterLast(" – ", "0").trim()) ?: 0
        } else {
            element.select("a").let{
                chapter.setUrlWithoutDomain(it.attr("href"))
                chapter.name = it.text()
            }
            chapter.date_upload = parseChapterDate(element.select("span i").text()) ?: 0
        }
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long? {
        return when {
            string == "0" -> 0
            "ago" in string -> parseRelativeDate(string) ?: 0
            else -> dateFormat.parse(string).time
        }
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.substringBefore(" ago").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "second" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("https://getmanhwa.co/${chapter.url}")
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img.wp-manga-chapter-img").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        GenreFilter()
    )

    private class GenreFilter: UriPartFilter("Genre", arrayOf(
        Pair("All","all"),
        Pair("Romance","romance"),
        Pair("Drama","drama"),
        Pair("Comedy","comedy"),
        Pair("Fantasy","fantasy"),
        Pair("Action","action"),
        Pair("BL","bl"),
        Pair("GL","gl"),
        Pair("Horror","horror"),
        Pair("School Life","school-life")
    ))

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
