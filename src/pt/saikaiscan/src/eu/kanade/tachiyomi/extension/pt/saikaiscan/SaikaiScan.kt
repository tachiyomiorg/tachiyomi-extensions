package eu.kanade.tachiyomi.extension.pt.saikaiscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SaikaiScan : ParsedHttpSource() {

    override val name = "Saikai Scan"

    override val baseUrl = "https://saikaiscan.com.br"

    override val lang = "pt"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div#menu ul li.has_submenu:eq(3) li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.text().substringBeforeLast("(")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "ul.manhuas li.manhua-item"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val image = element.select("div.image.lazyload")
        val name = element.select("h3")

        title = name.text().substringBeforeLast("(")
        thumbnail_url = baseUrl + image.attr("data-src")
        url = image.select("a").attr("href")
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/busca")!!.newBuilder()
            .addQueryParameter("q", query)

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val results = super.searchMangaParse(response)
        val manhuas = results.mangas.filter { it.url.contains("/manhuas/") }

        return MangasPage(manhuas, results.hasNextPage)
    }

    override fun searchMangaSelector(): String = "div#news-content ul li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val image = element.select("div.image.lazyload")
        val name = element.select("h3")

        title = name.text().substringBeforeLast("(")
        thumbnail_url = baseUrl + image.attr("data-src")
        url = image.select("a").attr("href")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val projectContent = document.select("div#project-content")
        val name = projectContent.select("h2").first()
        val cover = projectContent.select("div.cover img.lazyload")
        val genres = projectContent.select("div.info:contains(Gênero:)")
        val author = projectContent.select("div.info:contains(Autor:)")
        val status = projectContent.select("div.info:contains(Status:)")
        val summary = projectContent.select("div.summary-text")

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
        chapter_number = CHAPTER_REGEX.find(element.text())?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
        name = element.text()
        url = element.attr("href")
    }

    override fun pageListParse(document: Document): List<Page> {
        val imagesBlock = document.select("div.manhua-slide div.images-block img.lazyload")

        return imagesBlock
            .mapIndexed { i, el -> Page(i, "", el.absUrl("src")) }
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun removeLabel(info: String) = info.substringAfter(":")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private val CHAPTER_REGEX = "Capítulo (\\d+)".toRegex()
    }
}
