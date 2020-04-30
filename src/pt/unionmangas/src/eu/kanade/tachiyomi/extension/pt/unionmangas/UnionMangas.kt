package eu.kanade.tachiyomi.extension.pt.unionmangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class UnionMangas : ParsedHttpSource() {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 6931383302802153355

    override val name = "Union Mangás"

    override val baseUrl = "https://unionleitor.top"

    override val lang = "pt-BR"

    override val supportsLatest = true

    // Sometimes the site is very slow.
    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .connectTimeout(3, TimeUnit.MINUTES)
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(3, TimeUnit.MINUTES)
            .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/xz")

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/lista-mangas" + (if (page == 1) "" else "/visualizacoes/${page - 1}"))
            .build()

        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/lista-mangas/visualizacoes$pageStr", newHeaders)
    }

    override fun popularMangaSelector(): String = "div.bloco-manga"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a").last().text().withoutLanguage()
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
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/assets/noticias.php", newHeaders, form)
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
        val newHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val url = HttpUrl.parse("$baseUrl/assets/busca.php")!!.newBuilder()
            .addQueryParameter("q", query)

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        val mangas = result["items"].array
            .map { searchMangaFromObject(it.obj) }

        return MangasPage(mangas, false)
    }

    private fun searchMangaFromObject(obj: JsonObject): SManga = SManga.create().apply {
        title = obj["titulo"].string
        thumbnail_url = obj["imagem"].string
        setUrlWithoutDomain("$baseUrl/perfil-manga/${obj["url"].string}")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.tamanho-bloco-perfil").first()
        val elAuthor = infoElement.select("div.row:eq(2) div.col-md-8:eq(4)").first()
        val elArtist = infoElement.select("div.row:eq(2) div.col-md-8:eq(5)").first()
        val elGenre = infoElement.select("div.row:eq(2) div.col-md-8:eq(3)").first()
        val elStatus = infoElement.select("div.row:eq(2) div.col-md-8:eq(6)").first()
        val elDescription = infoElement.select("div.row:eq(2) div.col-md-8:eq(8)").first()
        val imgThumbnail = infoElement.select(".img-thumbnail").first()
        val elTitle = infoElement.select("h2").first()

        return SManga.create().apply {
            title = elTitle!!.text().withoutLanguage()
            author = elAuthor?.textWithoutLabel()
            artist = elArtist?.textWithoutLabel()
            genre = elGenre?.textWithoutLabel()
            status = parseStatus(elStatus?.text().orEmpty())
            description = elDescription?.text()
            thumbnail_url = imgThumbnail?.attr("src")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ativo") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.row.lancamento-linha"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val firstColumn = element.select("div.col-md-6:eq(0)")
        val secondColumn = element.select("div.col-md-6:eq(1)")

        name = firstColumn.select("a").first().text()
        scanlator = secondColumn?.text()
        date_upload = DATE_FORMATTER.tryParseTime(firstColumn.select("span").last()!!.text())
        setUrlWithoutDomain(firstColumn.select("a").first().attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.img-responsive.img-manga")
            .filter { it.attr("src").contains("/leitor/") }
            .mapIndexed { i, element -> Page(i, document.location(), element.absUrl("src")) }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun searchMangaSelector() = throw Exception("This method should not be called!")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun searchMangaNextPageSelector() = throw Exception("This method should not be called!")

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/perfil-manga/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/perfil-manga/$id"
        return MangasPage(listOf(details), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun SimpleDateFormat.tryParseTime(date: String): Long {
        return try {
            parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.withoutLanguage(): String = replace("(Pt-Br)", "", true).trim()

    private fun Element.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }

        const val PREFIX_ID_SEARCH = "id:"

        private val DATE_FORMATTER by lazy { SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH) }
    }
}
