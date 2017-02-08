package eu.kanade.tachiyomi.extension.en.perveden

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Perveden : ParsedHttpSource() {

    override val name = "PervEden"

    override val baseUrl = "http://www.perveden.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/en/en-directory/?order=3&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/en/en-directory/?order=1&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/en/en-directory/").newBuilder().addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is StatusList -> filter.state
                        .filter { it.state }
                        .map { it.id.toString() }
                        .forEach { url.addQueryParameter("status", it) }
                is Types -> filter.state
                        .filter { it.state }
                        .map { it.id.toString() }
                        .forEach { url.addQueryParameter("type", it) }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is OrderBy -> filter.state?.let {
                    val sortId = it.index
                    url.addQueryParameter("order", if (it.ascending) "-$sortId" else "$sortId")
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table#mangaList > tbody > tr:has(td:gt(1))"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("td > a").first()?.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infos = document.select("div.rightbox")

        author = infos.select("a[href^=/en/en-directory/?author]").first()?.text()
        artist = infos.select("a[href^=/en/en-directory/?artist]").first()?.text()
        genre = infos.select("a[href^=/en/en-directory/?categoriesInc]").map { it.text() }.joinToString()
        description = document.select("h2#mangaDescription").text()
        status = parseStatus(infos.select("h4:containsOwn(Status)").first()?.nextSibling().toString())
        val img = infos.select("div.mangaImage2 > img").first()?.attr("src")
        if (!img.isNullOrBlank()) thumbnail_url = img.let { "http:$it" }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#leftContent > table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.select("a[href^=/en/en-manga/]").first()

        setUrlWithoutDomain(a.attr("href"))
        name = a?.select("b")?.first()?.text().orEmpty()
        date_upload = element.select("td.chapterDate").first()?.text()?.let { parseChapterDate(it.trim()) } ?: 0L
    }

    private fun parseChapterDate(date: String): Long =
            if ("Today" in date) {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else if ("Yesterday" in date) {
                Calendar.getInstance().apply {
                    add(Calendar.DATE, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else try {
                SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) {
                0L
            }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("option[value^=/en/en-manga/]").forEach {
            add(Page(size, "$baseUrl${it.attr("value")}"))
        }
    }

    override fun imageUrlParse(document: Document): String = document.select("a#nextA.next > img").first()?.attr("src").let { "http:$it" }

    private class NamedId(name: String, val id: Int) : Filter.CheckBox(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class OrderBy : Filter.Sort("Order by", arrayOf("Manga title", "Views", "Chapters", "Latest chapter"),
            Filter.Sort.Selection(1, false))

    private class StatusList(statuses: List<NamedId>) : Filter.Group<NamedId>("Stato", statuses)
    private class Types(types: List<NamedId>) : Filter.Group<NamedId>("Tipo", types)

    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            OrderBy(),
            Types(types()),
            StatusList(statuses())
    )

    private fun types() = listOf(
            NamedId("Japanese Manga", 0),
            NamedId("Korean Manhwa", 1),
            NamedId("Chinese Manhua", 2),
            NamedId("Comic", 3),
            NamedId("Doujinshi", 4)
    )

    private fun statuses() = listOf(
            NamedId("Ongoing", 1),
            NamedId("Completed", 2),
            NamedId("Suspended", 0)
    )
}