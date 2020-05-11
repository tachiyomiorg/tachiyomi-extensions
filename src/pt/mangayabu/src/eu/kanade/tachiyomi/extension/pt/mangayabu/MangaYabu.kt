package eu.kanade.tachiyomi.extension.pt.mangayabu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MangaYabu : ParsedHttpSource() {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 7152688036023311164

    override val name = "MangaYabu!"

    override val baseUrl = "https://mangayabu.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "main.home div.features:contains(Populares) div.feature > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val thumb = element.select("img").first()!!

        title = thumb.attr("alt").withoutFlags()
        thumbnail_url = thumb.attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return super.fetchLatestUpdates(page)
            .map {
                MangasPage(it.mangas.distinctBy { m -> m.url }, it.hasNextPage)
            }
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "main.home div.features:contains(Lançamentos) div.feature > a"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val thumb = element.select("img").first()!!

        title = thumb.attr("alt").substringBefore(" –").withoutFlags()
        thumbnail_url = thumb.attr("src")
        url = mapChapterToMangaUrl(element.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("action", "data_fetch")
            .add("search_keyword", query)
            .build()

        val newHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Length", form.contentLength().toString())
            .add("Content-Type", form.contentType().toString())
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", newHeaders, form)
    }

    override fun searchMangaSelector() = "ul li.gsuggested a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.contento span.search-name").first()!!.text()
        thumbnail_url = element.select("img.search-thumb")!!.attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.manga-single-list div.manga-info").first()!!
        val statusStr = infoElement.select("div.manga-status").first()!!.textWithoutLabel()

        return SManga.create().apply {
            title = infoElement.select("div.manga-title h1")!!.text()
            status = when (statusStr) {
                "Em lançamento" -> SManga.ONGOING
                "Completo" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = infoElement.select("div.manga-genres").first()!!.textWithoutLabel()
                .replace(" ,", ",")
            description = document.select("div.manga-synopsis").first()!!.text()
            thumbnail_url = infoElement.select("div.manga-cover img")!!.attr("src")
        }
    }

    override fun chapterListSelector() = "div.manga-single-list div.manga-chapters div.single-chapter"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").first()!!.text()
        date_upload = DATE_FORMATTER.tryParseTime(element.select("small")!!.text())
        setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.manga-pages img.img-responsive.img-manga")
            .mapIndexed { i, element ->
                Page(i, document.location(), element.attr("abs:src"))
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    /**
     * Some mangas doesn't use the same slug from the chapter url, and
     * since the site doesn't have a proper popular list yet, we have
     * to deal with some exceptions and map them to the correct
     * slug manually.
     *
     * It's a bad solution, but it's a working one for now.
     */
    private fun mapChapterToMangaUrl(chapterUrl: String): String {
        val chapterSlug = chapterUrl
            .substringBefore("-capitulo")
            .substringAfter("ler/")

        return "/manga/" + (SLUG_EXCEPTIONS[chapterSlug] ?: chapterSlug)
    }

    private fun String.withoutFlags(): String = replace(FLAG_REGEX, "").trim()

    private fun Element.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    private fun SimpleDateFormat.tryParseTime(date: String): Long {
        return try {
            parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36"

        private val FLAG_REGEX = "\\((Pt[-/]br|Scan)\\)".toRegex(RegexOption.IGNORE_CASE)

        private val DATE_FORMATTER by lazy { SimpleDateFormat("dd/MM/yy", Locale.ENGLISH) }

        private val SLUG_EXCEPTIONS = mapOf(
            "the-promised-neverland-yakusoku-no-neverland" to "yakusoku-no-neverland-the-promised-neverland"
        )
    }
}
