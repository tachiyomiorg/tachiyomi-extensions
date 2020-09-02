package eu.kanade.tachiyomi.extension.pt.goldenmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GoldenMangas : ParsedHttpSource() {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 6858719406079923084

    override val name = "Golden Mangás"

    override val baseUrl = "https://goldenmangas.top"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String =
        "div.section:contains(Mais Lídos) + div.section div.manga_item div.andro_product-thumb a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("img").attr("alt")
            .substringAfter("online ")
            .withoutLanguage()
        thumbnail_url = element.select("img").attr("abs:data-src")
        url = "/" + element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page > 1) "/index.php?pagina=$page" else ""
        return GET("$baseUrl$path", headers)
    }

    override fun latestUpdatesSelector() = "div.row.atualizacoes div.manga_item div.andro_product"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.select("h5.andro_product-title > a")

        title = titleElement.text().withoutLanguage()
        thumbnail_url = element.select("div.andro_product-thumb img").attr("abs:src")
        url = "/" + titleElement.attr("href")
    }

    override fun latestUpdatesNextPageSelector() = "ul.pagination li.active + li"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/mangas")
            .build()

        val url = HttpUrl.parse("$baseUrl/manga")!!.newBuilder()
            .addQueryParameter("busca", query)
            .toString()

        return GET(url, newHeaders)
    }

    override fun searchMangaSelector() = "div.container div.row:contains(Resultados) div.andro_product"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.select("p.andro_product-title a")

        title = titleElement.text().withoutLanguage()
        thumbnail_url = element.select("div.andro_product-thumb img").attr("abs:data-src")
        url = "/" + titleElement.attr("href")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.andro_subheader + div.section div.row").first()
        val firstColumn = infoElement.select("div.col-md-3 div.andro_product-single-thumb > img").first()
        val secondColumn = infoElement.select("div.col-md-9 div.andro_product-single-content").first()
        val metadata = secondColumn.select("div.row:eq(2) ul.andro_product-meta").first()

        title = secondColumn.select("div.row:eq(0) h3").text().withoutLanguage()
        author = metadata.select("li:eq(1) div a").text().trim()
        artist = metadata.select("li:eq(2) div a").text().trim()
        genre = metadata.select("li:eq(0) div a").joinToString { it.text() }
        status = metadata.select("li:eq(3) div a").text().toStatus()
        description = secondColumn.select("div.row:eq(3) p").text().trim()
        thumbnail_url = firstColumn.attr("abs:src")
    }

    /**
     * Need to override the method to get the API endpoint URL that
     * uses the manga id to return the chapter list.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterScript = response.asJsoup()
            .select("script:containsData(capitulos_cache.php)")
            .first()
        val chapterEndpointUrl = chapterScript.data()
            .substringAfter("url: \"")
            .substringBefore("\"")

        val chapterListHeaders = headersBuilder()
            .set("Accept", "*/*")
            .set("Referer", response.request().url().toString())
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val chapterRequest = GET("$baseUrl/$chapterEndpointUrl", chapterListHeaders)
        val chapterResponse = client.newCall(chapterRequest).execute()

        return super.chapterListParse(chapterResponse)
    }

    override fun chapterListSelector() = "div.andro_single-pagination-item div.row"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val firstColumn = element.select("div.col-sm-7").first()
        val secondColumn = element.select("div.col-sm-5 a")

        name = firstColumn.select("b").first().text()
        scanlator = secondColumn.joinToString { it.text() }
        date_upload = firstColumn.select("span[style]").last().text().toDate()
        url = "/" + firstColumn.select("a").attr("href")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url.substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.container_images_img img.img-responsive")
            .mapIndexed { i, element ->
                Page(i, document.location(), element.attr("abs:src"))
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this.trim())?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus() = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    private fun String.withoutLanguage(): String = replace(FLAG_REGEX, "").trim()

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/webp,image/apng,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.83 Safari/537.36"

        private val FLAG_REGEX = "\\((Pt[-/]br|Scan)\\)".toRegex(RegexOption.IGNORE_CASE)

        private val DATE_FORMATTER by lazy { SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH) }
    }
}
