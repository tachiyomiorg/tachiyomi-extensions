package eu.kanade.tachiyomi.extension.pt.saikaiscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SaikaiScan : ParsedHttpSource() {

    override val name = "Saikai Scan"

    override val baseUrl = "https://saikaiscan.com.br"

    override val lang = "pt"

    override val supportsLatest = true

    private val catalogHeaders = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        add("Host", "saikaiscan.com.br")
        add("Referer", baseUrl)
    }.build()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, catalogHeaders)

    override fun popularMangaSelector(): String = "div#menu ul li.has_submenu:eq(3) li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.text().substringBeforeLast("(")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, catalogHeaders)

    override fun latestUpdatesSelector(): String = "ul.manhuas li.manhua-item"

    override fun latestUpdatesFromElement(element: Element): SManga {
        var image = element.select("div.image.lazyload")
        var name = element.select("h3")

        return SManga.create().apply {
            title = name.text().substringBeforeLast("(")
            thumbnail_url = baseUrl + image.attr("data-src")
            url = image.select("a").attr("href")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/busca?q=$query", catalogHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        var results = super.searchMangaParse(response)
        var manhuas = results.mangas.filter { it.url.contains("/manhuas/") }

        return MangasPage(manhuas, results.hasNextPage)
    }

    override fun searchMangaSelector(): String = "div#news-content ul li"

    override fun searchMangaFromElement(element: Element): SManga {
        var image = element.select("div.image.lazyload")
        var name = element.select("h3")

        return SManga.create().apply {
            title = name.text().substringBeforeLast("(")
            thumbnail_url = baseUrl + image.attr("data-src")
            url = image.select("a").attr("href")
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        var projectContent = document.select("div#project-content")
        var name = projectContent.select("h2").first()
        var cover = projectContent.select("div.cover img.lazyload")
        var genres = projectContent.select("div.info:contains(Gênero)")
        var author = projectContent.select("div.info:contains(Autor)")
        var status = projectContent.select("div.info:contains(Status)")
        var summary = projectContent.select("div.summary-text")

        return SManga.create().apply {
            title = name.text()
            thumbnail_url = baseUrl + cover.attr("data-src")
            genre = removeLabel(genres.text())
            this.author = removeLabel(author.text())
            artist = removeLabel(author.text())
            this.status = parseStatus(removeLabel(status.text()))
            description = summary.text()
        }
    }

    private fun removeLabel(info: String) = info.substringAfter(":")

    private fun parseStatus(status: String) = when {
        status.contains("Completo") -> SManga.COMPLETED
        status.contains("Em Tradução", true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector(): String = "div#project-content div.project-chapters div.chapters ul li a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        scanlator = "Saikai Scan"
        chapter_number = CHAPTER_REGEX.toRegex().find(element.text())?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
        name = element.text()
        url = element.attr("href")
    }

    override fun pageListParse(document: Document): List<Page> {
        var imagesBlock = document.select("div.manhua-slide div.images-block img.lazyload")

        return imagesBlock
                .mapIndexed { i, el -> Page(i, "", el.absUrl("src")) }
    }

    override fun imageUrlParse(document: Document): String = ""

    companion object {
        private const val CHAPTER_REGEX = "Capítulo (\\d+)"
    }

}
