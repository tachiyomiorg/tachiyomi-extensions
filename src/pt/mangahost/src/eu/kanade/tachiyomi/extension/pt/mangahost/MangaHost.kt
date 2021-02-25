package eu.kanade.tachiyomi.extension.pt.mangahost

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaHost : ParsedHttpSource() {

    // Hardcode the id because the name was wrong and the language wasn't specific.
    override val id: Long = 3926812845500643354

    override val name = "Mangá Host"

    override val baseUrl = "https://mangahosted.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)

    private fun genericMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            val thumbnailEl = element.select("img")
            val thumbnailAttr = if (thumbnailEl.hasAttr("data-path")) "data-path" else "src"

            title = element.attr("title").withoutLanguage()
            thumbnail_url = thumbnailEl.attr(thumbnailAttr).toLargeUrl()
            setUrlWithoutDomain(element.attr("href").substringBeforeLast("-mh"))
        }

    override fun popularMangaRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "/mais-visualizados/page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/mangas$listPath")
            .build()

        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/mangas/mais-visualizados$pageStr", newHeaders)
    }

    override fun popularMangaSelector(): String = "div#dados div.manga-block div.manga-block-left a"

    override fun popularMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi:has(a.nextpostslink)"

    override fun latestUpdatesRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "/lancamentos/page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + listPath)
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

    override fun searchMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    /**
     * The site wrongly return 404 for some titles, even if they are present.
     * In those cases, the extension will parse the response normally.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableIgnoreCode(404)
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.box-content div.w-row div.w-col:eq(1) article")

        author = infoElement.select("div.text li div:contains(Autor:)").textWithoutLabel()
        artist = infoElement.select("div.text li div:contains(Arte:)").textWithoutLabel()
        genre = infoElement.select("h3.subtitle + div.tags a").joinToString { it.text() }
        description = infoElement.select("div.text div.paragraph").first()?.text()
            ?.substringBefore("Relacionados:")
        status = infoElement.select("div.text li div:contains(Status:)").text().toStatus()
        thumbnail_url = document.select("div.box-content div.w-row div.w-col:eq(0) div.widget img")
            .attr("src")
    }

    /**
     * The site wrongly return 404 for some titles, even if they are present.
     * In those cases, the extension will parse the response normally.
     */
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            client.newCall(chapterListRequest(manga))
                .asObservableIgnoreCode(404)
                .map(::chapterListParse)
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListSelector(): String =
        "article.article > section.clearfix div.chapters div.cap div.card.pop"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("div.pop-title").text().withoutLanguage()
        scanlator = element.select("div.pop-content small strong").text()
        date_upload = element.select("small.clearfix").text()
            .substringAfter("Adicionado em ")
            .toDate()
        chapter_number = element.select("div.pop-title span.btn-caps").text()
            .toFloatOrNull() ?: 1f
        setUrlWithoutDomain(element.select("div.tags a").attr("href"))

        if (scanlator!!.split("/").count() >= 5) {
            val scanlators = scanlator!!.split("/")
            scanlator = scanlators[0] + " e mais " + (scanlators.count() - 1)
        }
    }

    /**
     * The site wrongly return 404 for some chapters, even if they are present.
     * In those cases, the extension will parse the response normally.
     */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableIgnoreCode(404)
            .map(::pageListParse)
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
            .set("Accept", ACCEPT_IMAGE)
            .set("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun Call.asObservableIgnoreCode(code: Int): Observable<Response> {
        return asObservable().doOnNext { response ->
            if (!response.isSuccessful && response.code() != code) {
                response.close()
                throw Exception("HTTP error ${response.code()}")
            }
        }
    }

    private fun String.toDate(): Long {
        return try {
            DATE_FORMAT.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus() = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun String.withoutLanguage(): String = replace(LANG_REGEX, "")

    private fun String.toLargeUrl(): String = replace(IMAGE_REGEX, "_full.")

    private fun Elements.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.190 Safari/537.36"

        private val LANG_REGEX = "( )?\\((PT-)?BR\\)".toRegex()
        private val IMAGE_REGEX = "_(small|medium|xmedium)\\.".toRegex()

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
        }
    }
}
