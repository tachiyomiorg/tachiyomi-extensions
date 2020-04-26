package eu.kanade.tachiyomi.extension.pt.yesmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class YesMangas : ParsedHttpSource() {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 7187189302580957274

    override val name = "YES Mangás"

    override val baseUrl = "https://yesmangas1.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div#destaques div.three.columns a.img"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title").replace(LANG_REGEX, "")
        thumbnail_url = element.select("img").attr("data-path")
            .replace("xmedium", "xlarge")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "div#lancamentos table.u-full-width tbody tr td:eq(0) a"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title").replace(LANG_REGEX, "")
        thumbnail_url = element.select("img").attr("data-path")
            .replace("xmedium", "xlarge")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("q", query)

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector(): String = "tbody#leituras tr td:eq(0) a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("img").attr("alt").replace(LANG_REGEX, "")
        thumbnail_url = element.select("img").attr("data-path")
            .replace("medium", "xlarge")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        var container = document.select("div#descricao")
        var statusEl = container.select("ul li:contains(Status)")
        var authorEl = container.select("ul li:contains(Autor)")
        var artistEl = container.select("ul li:contains(Desenho)")
        var genresEl = container.select("ul li:contains(Categorias)")
        var synopsis = container.select("article")

        return SManga.create().apply {
            status = parseStatus(removeLabel(statusEl.text()))
            author = removeLabel(authorEl.text())
            artist = removeLabel(artistEl.text())
            description = synopsis.text().substringBefore("Relacionados")
            genre = removeLabel(genresEl.text())
            thumbnail_url = container.select("img").first().attr("data-path")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Completo") -> SManga.COMPLETED
        status.contains("Ativo") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector(): String = "div#capitulos a.button"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        chapter_number = element.text().toFloatOrNull() ?: 1f
        name = element.attr("title").substringAfter(" - ")
    }

    override fun pageListParse(document: Document): List<Page> {
        var script = document.select("script").last().data()
        var images = script.substringAfter(SCRIPT_BEGIN).substringBefore(SCRIPT_END)
                .replace(SCRIPT_REGEX.toRegex(), "")

        var newDocument = Jsoup.parse(images)

        return newDocument.select("a img")
                .mapIndexed { i, el -> Page(i, "", el.attr("src")) }
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun removeLabel(info: String) = info.substringAfter(":")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        private val LANG_REGEX = "( )?\\((PT-)?BR\\)".toRegex()

        private const val SCRIPT_BEGIN = "var images = ["
        private const val SCRIPT_END = "];"
        private const val SCRIPT_REGEX = "\"|,"
    }
}
