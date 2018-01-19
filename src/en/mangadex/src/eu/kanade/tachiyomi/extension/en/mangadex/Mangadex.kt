package eu.kanade.tachiyomi.extension.en.mangadex

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

class Mangadex : ParsedHttpSource() {

    override val name = "MangaDex"

    override val baseUrl = "https://mangadex.com"

    override val supportsLatest = true

    override val lang = "en"

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun latestUpdatesSelector() = ".table-responsive tbody tr"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/?page=search", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return latestUpdatesFromElement(element)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[href*=manga]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
            manga.author = it.text().trim()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/?page=search")!!.newBuilder().addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = ".table.table-striped.table-hover.table-condensed tbody tr"

    override fun searchMangaFromElement(element: Element): SManga {
        return latestUpdatesFromElement(element)
    }

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val imageElement = document.select(".table-condensed").first()
        val infoElement = document.select(".table.table-condensed.edit").first()

        manga.author = infoElement.select("tr:eq(1) td").first()?.text()
        manga.artist = infoElement.select("tr:eq(2) td").first()?.text()
        manga.status = parseStatus(infoElement.select("tr:eq(5) td").first()?.text())
        manga.description = infoElement.select("tr:eq(7) td").first()?.text()
        manga.thumbnail_url = imageElement.select("img").first()?.attr("src").let { baseUrl + "/" + it }
        manga.genre = infoElement.select("tr:eq(3) td").first()?.text()

        return manga
    }

    override fun chapterListSelector() = ".table.table-striped.table-hover.table-condensed tr:not(:first-child)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td:eq(0)").first()
        val dateElement = element.select("td:eq(6)").first()
        val scanlatorElement = element.select("td:eq(3)").first()

        val chapter = SChapter.create()
        chapter.url = (urlElement.select("a").attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = dateElement?.attr("title")?.let { parseChapterDate(it.removeSuffix(" UTC")) } ?: 0
        chapter.scanlator = scanlatorElement?.text()
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(date).time
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val url = document.baseUri()
        document.select("#jump_page").first().select("option").forEach {
            pages.add(Page(pages.size, url + "/" + it.attr("value")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = baseUrl + document.select("#current_page").first().attr("src")

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        status.contains("Licensed") -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)

    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist")
    )

}