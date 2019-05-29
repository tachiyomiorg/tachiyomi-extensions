package eu.kanade.tachiyomi.extension.pt.unionmangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class UnionMangas : ParsedHttpSource() {

    override val name = "Union Mangás"

    override val baseUrl = "https://unionmangas.top"

    override val lang = "pt"

    override val supportsLatest = true

    // Sometimes the site is very slow.
    override val client =
            network.client.newBuilder()
                    .connectTimeout(3, TimeUnit.MINUTES)
                    .readTimeout(3, TimeUnit.MINUTES)
                    .writeTimeout(3, TimeUnit.MINUTES)
                    .build()

    private val catalogHeaders = Headers.Builder()
            .apply {
                add("User-Agent", USER_AGENT)
                add("Origin", baseUrl)
                add("Referer", "$baseUrl/ini")
            }
            .build()

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "/$page" else ""
        return GET("$baseUrl/lista-mangas/visualizacoes$pageStr", catalogHeaders)
    }

    override fun popularMangaSelector(): String = "div.bloco-manga"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = removeLanguage(element.select("a").last().text())
        thumbnail_url = element.select("a img").first()?.attr("src")
        setUrlWithoutDomain(element.select("a").last().attr("href"))
    }

    override fun popularMangaNextPageSelector() = ".pagination li:contains(Next)"

    override fun latestUpdatesRequest(page: Int): Request {
        val form = FormBody.Builder()
                .add("pagina", page.toString())
                .build()

        val newHeaders = catalogHeaders.newBuilder()
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

        return POST("$baseUrl/assets/noticias.php", newHeaders, form)
    }

    override fun latestUpdatesSelector() = "div.row[style] div.col-md-12[style]"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val infoElement = element.select("a.link-titulo")

        return SManga.create().apply {
            title = removeLanguage(infoElement.last().text())
            thumbnail_url = infoElement.first()?.select("img")?.attr("src")
            setUrlWithoutDomain(infoElement.last().attr("href"))
        }
    }

    override fun latestUpdatesNextPageSelector() = "div#linha-botao-mais"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = catalogHeaders.newBuilder()
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

        val url = HttpUrl.parse("$baseUrl/assets/busca.php")!!.newBuilder()
                .addQueryParameter("q", query)

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        val mangas = result["items"].array.map { searchMangaFromObject(it.obj) }

        return MangasPage(mangas, false)
    }

    private fun searchMangaFromObject(obj: JsonObject): SManga = SManga.create().apply {
        title = obj["titulo"].string
        thumbnail_url = obj["imagem"].string
        setUrlWithoutDomain("$baseUrl/manga/${obj["url"].string}")
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, catalogHeaders)

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.tamanho-bloco-perfil").first()
        val elAuthor = infoElement.select("div.row:eq(2) div.col-md-8:eq(4)").first()
        val elArtist = infoElement.select("div.row:eq(2) div.col-md-8:eq(5)").first()
        val elGenre = infoElement.select("div.row:eq(2) div.col-md-8:eq(3)").first()
        val elStatus =  infoElement.select("div.row:eq(2) div.col-md-8:eq(6)").first()
        val elDescription = infoElement.select("div.row:eq(2) div.col-md-8:eq(8)").first()
        val imgThumbnail = infoElement.select(".img-thumbnail").first()
        val elTitle = infoElement.select("h2").first()

        return SManga.create().apply {
            title = removeLanguage(elTitle!!.text())
            author = removeLabel(elAuthor?.text())
            artist = removeLabel(elArtist?.text())
            genre = removeLabel(elGenre?.text())
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

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, catalogHeaders)

    override fun chapterListSelector() = "div.row.lancamento-linha"

    override fun chapterFromElement(element: Element): SChapter {
        val firstColumn = element.select("div.col-md-6:eq(0)")
        val secondColumn = element.select("div.col-md-6:eq(1)")

        return SChapter.create().apply {
            name = firstColumn.select("a").first().text()
            scanlator = secondColumn?.text()
            date_upload = parseChapterDate(firstColumn.select("span").last()!!.text())
            setUrlWithoutDomain(firstColumn.select("a").first().attr("href"))
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, catalogHeaders)

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select("img.img-responsive.img-manga")

        return pages
                .filter { it.attr("src").contains("leitor") }
                .mapIndexed { i, element -> Page(i, "", element.absUrl("src"))}
    }

    override fun imageUrlParse(document: Document) = ""

    override fun searchMangaSelector() = throw Exception("This method should not be called!")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun searchMangaNextPageSelector() = throw Exception("This method should not be called!")

    private fun removeLanguage(text: String): String = text.replace("(Pt-Br)", "", true).trim()

    private fun removeLabel(text: String?): String = text!!.substringAfter(":").trim()

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }
    }
}
