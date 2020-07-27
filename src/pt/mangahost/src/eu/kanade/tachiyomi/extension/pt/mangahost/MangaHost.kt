package eu.kanade.tachiyomi.extension.pt.mangahost

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class MangaHost : ParsedHttpSource() {

    // Hardcode the id because the name was wrong and the language wasn't specific.
    override val id: Long = 3926812845500643354

    override val name = "Mangá Host"

    override val baseUrl = "https://mangahost2.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    private fun genericMangaFromElement(element: Element, attr: String = "src"): SManga =
        SManga.create().apply {
            title = element.attr("title").withoutLanguage()
            thumbnail_url = element.select("img").attr(attr).toLargeUrl()
            setUrlWithoutDomain(element.attr("href").substringBeforeLast("-mh"))
        }

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/mangas" + (if (page == 1) "" else "/mais-visualizados/page/${page - 1}"))
            .build()

        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/mangas/mais-visualizados$pageStr", newHeaders)
    }

    override fun popularMangaSelector(): String = "div#dados div.manga-block div.manga-block-left a"

    override fun popularMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi:has(a.nextpostslink)"

    override fun latestUpdatesRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + (if (page == 1) "" else "/lancamentos/page/${page - 1}"))
            .build()

        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/lancamentos$pageStr", newHeaders)
    }

    override fun latestUpdatesSelector() = "div#dados div.line-lancamentos div.column-img a"

    override fun latestUpdatesFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/find/")!!.newBuilder()
            .addQueryParameter("this", query)

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table.table-search > tbody > tr > td:eq(0) > a"

    override fun searchMangaFromElement(element: Element): SManga = genericMangaFromElement(element, "data-path")

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.box-content div.w-row div.w-col:eq(1) article")

        return SManga.create().apply {
            author = infoElement.select("div.text li div:contains(Autor:)").textWithoutLabel()
            artist = infoElement.select("div.text li div:contains(Arte:)").textWithoutLabel()
            genre = infoElement.select("h3.subtitle + div.tags a").joinToString { it.text() }
            description = infoElement.select("div.text div.paragraph").first()?.text()
                ?.substringBefore("Relacionados:")
            status = infoElement.select("div.text li div:contains(Status:)").text().toStatus()
            thumbnail_url = document.select("div.box-content div.w-row div.w-col:eq(0) div.widget img")
                .attr("src")
        }
    }

    override fun chapterListSelector(): String =
        "article.article > section.clearfix div.chapters div.cap div.card.pop"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("div.pop-title").text().withoutLanguage()
        scanlator = element.select("div.pop-content small strong").text()
        date_upload = element.select("small.clearfix").text()
            .substringAfter("Adicionado em ")
            .let { DATE_FORMAT.tryParseTime(it) }
        chapter_number = element.select("div.pop-title span.btn-caps").text()
            .toFloatOrNull() ?: 1f
        setUrlWithoutDomain(element.select("div.tags a").attr("href"))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Just to prevent the detection of the crawler.
        val newHeader = headersBuilder()
            .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeader)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#slider a img")
            .mapIndexed { i, el -> Page(i, document.location(), el.attr("src")) }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun String.toStatus() = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun SimpleDateFormat.tryParseTime(date: String): Long {
        return try {
            parse(date)!!.time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.withoutLanguage(): String = replace(LANG_REGEX, "")

    private fun String.toLargeUrl(): String = replace(IMAGE_REGEX, "_large.")

    private fun Elements.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36"

        private val LANG_REGEX = "( )?\\((PT-)?BR\\)".toRegex()
        private val IMAGE_REGEX = "_(small|medium|xmedium)\\.".toRegex()

        private val DATE_FORMAT by lazy { SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH) }
    }
}
