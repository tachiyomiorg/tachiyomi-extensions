package eu.kanade.tachiyomi.extension.pt.goldenmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    override fun popularMangaSelector(): String = "div#maisLidos div.itemmanga"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3").text().withoutLanguage()
        thumbnail_url = element.select("img").attr("abs:src")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page > 1) "/index.php?pagina=$page" else ""
        return GET("$baseUrl$path", headers)
    }

    override fun latestUpdatesSelector() = "div.col-sm-12.atualizacao > div.row"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val infoElement = element.select("div.col-sm-10.col-xs-8 h3").first()
        val thumbElement = element.select("a:first-child div img").first()

        title = infoElement.text().withoutLanguage()
        thumbnail_url = thumbElement.attr("abs:src")
            .replace("w=80&h=120", "w=380&h=600")
        url = element.select("a:first-child").attr("href")
    }

    override fun latestUpdatesNextPageSelector() = "ul.pagination li:last-child a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/mangas")
            .build()

        val url = HttpUrl.parse("$baseUrl/mangas")!!.newBuilder()
            .addQueryParameter("busca", query)
            .toString()

        return GET(url, newHeaders)
    }

    override fun searchMangaSelector() = "div.mangas.col-lg-2 a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3").text().withoutLanguage()
        thumbnail_url = element.select("img").attr("abs:src")
        url = element.attr("href")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.row > div.col-sm-8 > div.row").first()
        val firstColumn = infoElement.select("div.col-sm-4.text-right > img").first()
        val secondColumn = infoElement.select("div.col-sm-8").first()

        title = secondColumn.select("h2:eq(0)").text().withoutLanguage()
        author = secondColumn.select("h5:eq(3)")!!.text().withoutLabel()
        artist = secondColumn.select("h5:eq(4)")!!.text().withoutLabel()
        genre = secondColumn.select("h5:eq(2) a")
            .filter { it.text().isNotEmpty() }
            .joinToString { it.text() }
        status = secondColumn.select("h5:eq(5) a").text().toStatus()
        description = document.select("#manga_capitulo_descricao").text()
        thumbnail_url = firstColumn.attr("abs:src")
    }

    override fun chapterListSelector() = "ul#capitulos li.row"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val firstColumn = element.select("a > div.col-sm-5")
        val secondColumn = element.select("div.col-sm-5.text-right a[href^='http']")

        name = firstColumn.select("div.col-sm-5").first().text()
            .substringBefore("(").trim()
        scanlator = secondColumn?.joinToString { it.text() }
        date_upload = firstColumn.select("div.col-sm-5 span[style]").text().toDate()
        url = element.select("a").attr("href")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url.substringBeforeLast("/"))
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapterImages = document.select("div.col-sm-12[id^='capitulos_images']").first()

        return chapterImages.select("img[pag]")
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

    private fun String.withoutLabel(): String = substringAfter(":").trim()

    private fun String.withoutLanguage(): String = replace(FLAG_REGEX, "").trim()

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/webp,image/apng,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36"

        private val FLAG_REGEX = "\\((Pt[-/]br|Scan)\\)".toRegex(RegexOption.IGNORE_CASE)

        private val DATE_FORMATTER by lazy { SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH) }
    }
}
