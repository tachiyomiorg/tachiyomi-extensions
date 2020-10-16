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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

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
        title = element.attr("title").withoutLanguage()
        thumbnail_url = element.select("img").attr("data-path").toLargeUrl()
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "div#lancamentos table.u-full-width tbody tr td:eq(0) a"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("title").withoutLanguage()
        thumbnail_url = element.select("img").attr("data-path").toLargeUrl()
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
        title = element.attr("title").withoutLanguage()
        thumbnail_url = element.select("img").attr("data-path").toLargeUrl()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val container = document.select("div#descricao").first()

        author = container.select("ul li:contains(Autor)").textWithoutLabel()
        artist = container.select("ul li:contains(Desenho)").textWithoutLabel()
        genre = container.select("ul li:contains(Categorias)").textWithoutLabel()
        status = container.select("ul li:contains(Status)").text().toStatus()
        description = container.select("article").text()
            .substringBefore("Relacionados")
        thumbnail_url = container.select("img").first()
            .attr("data-path")
            .toLargeUrl()
    }

    override fun chapterListSelector(): String = "div#capitulos a.button"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.attr("title").substringAfter(" - ")
        chapter_number = element.text().toFloatOrNull() ?: -1f
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url.substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.read-slideshow a img")
            .mapIndexed { i, el -> Page(i, document.location(), el.attr("src")) }
    }

    override fun imageUrlParse(document: Document): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun String.withoutLanguage(): String = replace(LANG_REGEX, "")

    private fun String.toLargeUrl(): String = replace(IMAGE_REGEX, "_full.")

    private fun Elements.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    private fun String.toStatus() = when {
        contains("Completo") -> SManga.COMPLETED
        contains("Ativo") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.121 Safari/537.36"

        private val LANG_REGEX = "( )?\\((PT-)?BR\\)".toRegex()
        private val IMAGE_REGEX = "_(small|medium|xmedium|xlarge)\\.".toRegex()
    }
}
