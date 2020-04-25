package eu.kanade.tachiyomi.extension.pt.zinnes

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Zinnes : ParsedHttpSource() {

    override val name = "Zinnes"

    override val baseUrl = "https://www.zinnes.com.br"

    override val lang = "pt"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "section#top > ul > li > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val label = element.select("div.label")

        title = label.select("h3.titulo").text()
        thumbnail_url = baseUrl + "/" + element.select("img").first().attr("data-temp")
        setUrlWithoutDomain(baseUrl + element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val length = QTD_REGEX.find(document.toString())!!.groupValues[1].toInt()
        val page = PAGE_REGEX.find(document.toString())!!.groupValues[1].toInt()

        // From the JavaScript of the page.
        return MangasPage(mangas, length - page * 20 > 0)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/pesquisa/comic/$page/")!!.newBuilder()
            .addEncodedPathSegment(query)

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "ul#resultados > li > a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val div = element.select("div").first()

        title = div.select("h4").text()

        val thumb = element.select("img").first().attr("src")
        val dataTemp = element.select("img").first().attr("data-temp")
        thumbnail_url = baseUrl + "/" + (if (thumb != NO_THUMB) dataTemp else thumb)

        setUrlWithoutDomain(baseUrl + element.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("#titulo > h1").text()
        author = document.select("#autor").text()
        artist = document.select("#autor").text()
        genre = document.select("#genero").text()
        status = SManga.UNKNOWN
        description = document.select("#descricao > p").last().text()

        val thumbImg = document.select("#imagem-projeto > img").first()
        thumbnail_url = baseUrl + "/" + (if (thumbImg != null) thumbImg.attr("src") else NO_THUMB)
    }

    override fun chapterListSelector() = "div#capitulos > ul > li.capitulo > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("header > h3").text()
        date_upload = parseChapterDate(element.select("div.capitulo-info > span.data").text())
        setUrlWithoutDomain(baseUrl + "/" + element.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val id = ID_REGEX.find(document.toString())!!.groupValues[1]

        return FILES_REGEX.find(document.toString())!!.groupValues[1]
            .replace("\"", "")
            .split(",")
            .mapIndexed { i, img -> Page(i, "", "$baseUrl/servidor/titulos/comics/$id/$img") }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("This method should not be called!")

    override fun latestUpdatesSelector() = throw Exception("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("dd MMM, yyyy", Locale("pt", "BR")).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"

        private val ID_REGEX = "var titulo = \\{\"id\":\"(\\d+)\"".toRegex()
        private val FILES_REGEX = "var arquivos = \\[(.*)\\];".toRegex()
        private val QTD_REGEX = "var qtd = (\\d+);".toRegex()
        private val PAGE_REGEX = "var pagina = '(\\d+)';".toRegex()

        private const val NO_THUMB = "img/sem-foto.png"
    }
}
