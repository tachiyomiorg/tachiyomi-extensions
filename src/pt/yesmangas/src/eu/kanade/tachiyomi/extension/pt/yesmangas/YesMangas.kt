package eu.kanade.tachiyomi.extension.pt.yesmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class YesMangas : ParsedHttpSource() {

    override val name = "YES Mangás"

    override val baseUrl = "https://yesmangasbr.com"

    override val lang = "pt"

    override val supportsLatest = true

    private val catalogHeaders = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        add("Host", "yesmangasbr.com")
        add("Referer", baseUrl)
    }.build()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, catalogHeaders)

    override fun popularMangaSelector(): String = "div#destaques div.three.columns a.img"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title").replace(LANG_REGEX.toRegex(), "")
        thumbnail_url = element.select("img").attr("data-path")
                .replace("xmedium", "xlarge")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, catalogHeaders)

    override fun latestUpdatesSelector(): String = "div#lancamentos table.u-full-width tbody tr td:eq(0) a"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title").replace(LANG_REGEX.toRegex(), "")
        thumbnail_url = element.select("img").attr("data-path")
                .replace("medium", "xlarge")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query", catalogHeaders)
    }

    override fun searchMangaSelector(): String = "tbody#leituras tr td:eq(0) a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("img").attr("alt").replace(LANG_REGEX.toRegex(), "")
        thumbnail_url = element.select("img").attr("data-path").replace("medium", "xlarge")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        var container = document.select("div#descricao")
        var status = container.select("ul li:contains(Status)")
        var author = container.select("ul li:contains(Autor)")
        var artist = container.select("ul li:contains(Desenho)")
        var synopsis = container.select("article")

        return SManga.create().apply {
            this.status = parseStatus(removeLabel(status.text()))
            this.author = removeLabel(author.text())
            this.artist = removeLabel(artist.text())
            description = synopsis.text().substringBefore("Relacionados")
        }
    }

    private fun removeLabel(info: String) = info.substringAfter(":")

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

    companion object {
        private const val LANG_REGEX = "( )?\\((PT-)?BR\\)"
        private const val SCRIPT_BEGIN = "var images = ["
        private const val SCRIPT_END = "];"
        private const val SCRIPT_REGEX = "\"|,"
    }
}
