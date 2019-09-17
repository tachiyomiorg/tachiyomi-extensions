package eu.kanade.tachiyomi.extension.pt.goldenmangas

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GoldenMangas : ParsedHttpSource() {

    override val name = "Golden Mangás"

    override val baseUrl = "https://goldenmanga.top"

    override val lang = "pt"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.itemmanga"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = removeLanguage(element.select("h3").first().text())
        thumbnail_url = baseUrl + element.select("img").first()?.attr("src")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page > 1) "/index.php?pagina=$page" else ""
        return GET("$baseUrl$path", headers)
    }

    override fun latestUpdatesSelector() = "div.col-sm-12.atualizacao > div.row"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        Log.d("golden", element.html())
        val infoElement = element.select("div.col-sm-10.col-xs-8 h3").first()
        val thumb = element.select("a:first-child div img").first().attr("src")

        title = removeLanguage(infoElement.text())
        thumbnail_url = baseUrl + thumb.replace("w=80&h=120", "w=380&h=600")
        url = element.select("a:first-child").attr("href")
    }

    override fun latestUpdatesNextPageSelector() = "ul.pagination li:last-child a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/mangas")
            .build()

        val url = HttpUrl.parse("$baseUrl/mangabr")!!.newBuilder()
            .addQueryParameter("busca", query)

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaSelector() = "div.mangas.col-lg-2 a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = removeLanguage(element.select("h3").first().text())
        thumbnail_url = baseUrl + element.select("img").first().attr("src")
        url = element.attr("href")
    }

    override fun searchMangaNextPageSelector() = "ul.pagination li:last-child a"

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.row > div.col-sm-8 > div.row").first()
        val firstColumn = infoElement.select("div.col-sm-4.text-right > img").first()
        val secondColumn = infoElement.select("div.col-sm-8").first()

        title = removeLanguage(secondColumn.select("h2:eq(1)").text())
        author = removeLabel(secondColumn.select("h5:eq(3)").text())
        artist = removeLabel(secondColumn.select("h5:eq(4)").text())
        genre = secondColumn.select("h5:eq(2) a")
            .filter { it.text().isNotEmpty() }
            .joinToString { it.text() }
        status = parseStatus(secondColumn.select("h5:eq(5) a").text().orEmpty())
        description = document.select("#manga_capitulo_descricao").text()
        thumbnail_url = baseUrl + firstColumn.attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ativo") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul#capitulos li.row"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val firstColumn = element.select("a > div.col-sm-5")
        val secondColumn = element.select("div.col-sm-5.text-right a[href^='http']")

        name = firstColumn.select("div.col-sm-5").first().text().substringBefore("(").trim()
        scanlator = secondColumn?.joinToString { it.text() }
        date_upload = parseChapterDate(firstColumn.select("div.col-sm-5 span[style]").first().text())
        url = element.select("a").first().attr("href")
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select("div.col-sm-12[id^='capitulos_images'] img[pag]")

        return pages
            .mapIndexed { i, element -> Page(i, "", baseUrl + element.attr("src"))}
    }

    override fun imageUrlParse(document: Document) = ""

    private fun removeLanguage(text: String): String = text.replace(FLAG_REGEX, "").trim()

    private fun removeLabel(text: String?): String = text!!.substringAfter(":").trim()

    private fun parseChapterDate(date: String) : Long {
        return try {
            SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private val FLAG_REGEX = "\\((Pt-br|Scan)\\)".toRegex(RegexOption.IGNORE_CASE)
    }
}
