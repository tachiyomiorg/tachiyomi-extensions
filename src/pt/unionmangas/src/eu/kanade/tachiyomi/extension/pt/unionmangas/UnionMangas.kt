package eu.kanade.tachiyomi.extension.pt.unionmangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class UnionMangas : ParsedHttpSource() {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 6931383302802153355

    override val name = "Union Mangás"

    override val baseUrl = "https://unionmangas.top"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(3, TimeUnit.MINUTES)
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(3, TimeUnit.MINUTES)
        .addInterceptor(RateLimitInterceptor(1, 2, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", "$baseUrl/home")

    override fun popularMangaRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "/visualizacoes/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/lista-mangas$listPath")
            .build()

        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/lista-mangas/visualizacoes$pageStr", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val results = super.popularMangaParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun popularMangaSelector(): String = "div.col-md-3.col-xs-6:has(img.img-thumbnail)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div[id^=bloco-tooltip] > b").first().text().withoutLanguage()
        thumbnail_url = element.select("a img").first()?.attr("src")
        setUrlWithoutDomain(element.select("a").last().attr("href"))
    }

    override fun popularMangaNextPageSelector() = ".pagination li:contains(Next)"

    override fun latestUpdatesRequest(page: Int): Request {
        val form = FormBody.Builder()
            .add("pagina", page.toString())
            .build()

        val newHeaders = headersBuilder()
            .add("Content-Type", form.contentType().toString())
            .add("Content-Length", form.contentLength().toString())
            .add("Origin", baseUrl)
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Accept", "*/*")
            .build()

        return POST("$baseUrl/assets/noticias.php", newHeaders, form)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val results = super.latestUpdatesParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun latestUpdatesSelector() = "div.row[style] div.col-md-12[style]"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val infoElement = element.select("a.link-titulo")

        title = infoElement.last().text().withoutLanguage()
        thumbnail_url = infoElement.first()?.select("img")?.attr("src")
        setUrlWithoutDomain(infoElement.last().attr("href"))
    }

    override fun latestUpdatesNextPageSelector() = "div#linha-botao-mais"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            return GET("$baseUrl/pagina-manga/$slug", headers)
        }

        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_JSON)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val url = "$baseUrl/assets/busca.php".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("titulo", query)

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()

        if (requestUrl.contains("pagina-manga")) {
            val slug = requestUrl.substringAfter("pagina-manga/")
            val manga = mangaDetailsParse(response)
                .apply { url = "/pagina-manga/$slug" }
            return MangasPage(listOf(manga), false)
        }

        val result = response.asJson().nullObj
            ?: throw Exception(BLOCK_MESSAGE)

        val mangas = result["items"].array
            .map { searchMangaFromObject(it.obj) }

        return MangasPage(mangas, false)
    }

    private fun searchMangaFromObject(obj: JsonObject): SManga = SManga.create().apply {
        title = obj["titulo"].string.withoutLanguage()
        thumbnail_url = obj["imagem"].string
        setUrlWithoutDomain("$baseUrl/pagina-manga/${obj["url"].string}")
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // Map the mangas that are already in library with the old URL to the new one.
        val newUrl = manga.url.replace("perfil-manga", "pagina-manga")
        return GET(baseUrl + newUrl, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.perfil-manga").firstOrNull()
            ?: throw Exception(BLOCK_MESSAGE)
        val rowInfo = infoElement.select("div.row:eq(2)").first()

        title = infoElement.select("h2").first().text().withoutLanguage()
        author = rowInfo.select("div.col-md-8:eq(4)").first().textWithoutLabel()
        artist = rowInfo.select("div.col-md-8:eq(5)").first().textWithoutLabel()
        genre = rowInfo.select("div.col-md-8:eq(3)").first().textWithoutLabel()
        status = rowInfo.select("div.col-md-8:eq(6)").first().text().toStatus()
        description = rowInfo.select("div.col-md-8:eq(8)").first().text()
        thumbnail_url = infoElement.select(".img-thumbnail").first().attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val results = super.chapterListParse(response)

        if (results.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun chapterListSelector() = "div.row.capitulos"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val firstColumn = element.select("div.col-md-6:eq(0)").first()!!
        val secondColumn = element.select("div.col-md-6:eq(1)").firstOrNull()

        name = firstColumn.select("a").first().text()
        scanlator = secondColumn?.select("a")?.joinToString { it.text() }
        date_upload = firstColumn.select("span").last()!!.text().toDate()

        // For some reason, setUrlWithoutDomain does not work when the url have spaces.
        val absoluteUrlFixed = firstColumn.select("a").first()
            .attr("href")
            .replace(" ", "%20")
        setUrlWithoutDomain(absoluteUrlFixed)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.img-responsive.img-manga")
            .filter { it.attr("src").contains("/leitor/") }
            .mapIndexed { i, element ->
                Page(i, document.location(), element.absUrl("src"))
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

    override fun searchMangaSelector() = throw Exception("This method should not be called!")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun searchMangaNextPageSelector() = throw Exception("This method should not be called!")

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus(): Int = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun String.withoutLanguage(): String =
        replace("(pt-br)", "", true).trim()

    private fun Element.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    private fun Response.asJson(): JsonElement = JSON_PARSER.parse(body!!.string())

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.128 Safari/537.36"

        private const val BLOCK_MESSAGE = "O site está bloqueando o Tachiyomi. " +
            "Migre para outra fonte caso o problema persistir."

        private val JSON_PARSER by lazy { JsonParser() }

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH)
        }

        const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
