package eu.kanade.tachiyomi.extension.en.vizshonenjump

import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.obj
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class VizShonenJump : ParsedHttpSource() {

    override val name = "VIZ Shonen Jump"

    override val baseUrl = "https://www.viz.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::authCheckIntercept)
        .addInterceptor(::authChapterCheckIntercept)
        .addInterceptor(VizImageInterceptor())
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/shonenjump")

    private var mangaList: List<SManga>? = null

    private var loggedIn: Boolean? = null

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl)
            .build()

        return GET("$baseUrl/shonenjump", newHeaders, CacheControl.FORCE_NETWORK)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangasPage = super.popularMangaParse(response)

        if (mangasPage.mangas.isEmpty())
            throw Exception(COUNTRY_NOT_SUPPORTED)

        mangaList = mangasPage.mangas.sortedBy { it.title }

        return mangasPage
    }

    override fun popularMangaSelector(): String =
        "section.section_chapters div.o_sort_container div.o_sortable > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.pad-x-rg").first().text()
        thumbnail_url = element.select("div.pos-r img.disp-bl").first()
            ?.attr("data-original")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangasPage = super.latestUpdatesParse(response)

        if (mangasPage.mangas.isEmpty())
            throw Exception(COUNTRY_NOT_SUPPORTED)

        mangaList = mangasPage.mangas.sortedBy { it.title }

        return mangasPage
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map {
                val filteredMangas = it.mangas.filter { m -> m.title.contains(query, true) }
                MangasPage(filteredMangas, it.hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = super.searchMangaParse(response)

        if (mangasPage.mangas.isEmpty())
            throw Exception(COUNTRY_NOT_SUPPORTED)

        return mangasPage
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val seriesIntro = document.select("section#series-intro").first()

        // Get the thumbnail url from the manga list (if available),
        // or fetch it for the first time (in backup restore, for example).
        if (mangaList == null) {
            val request = popularMangaRequest(1)
            val response = client.newCall(request).execute()
            // Call popularMangaParse to fill the manga list.
            popularMangaParse(response)
        }

        val mangaUrl = document.location().substringAfter(baseUrl)
        val mangaFromList = mangaList!!.firstOrNull { it.url == mangaUrl }

        return SManga.create().apply {
            author = seriesIntro.select("div.type-rg span").firstOrNull()?.text()
                ?.replace("Created by ", "")
            artist = author
            status = SManga.ONGOING
            description = seriesIntro.select("h4").firstOrNull()?.text()
            thumbnail_url = mangaFromList?.thumbnail_url ?: ""
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = super.chapterListParse(response)

        if (loggedIn == true) {
            return allChapters.map { oldChapter ->
                oldChapter.apply {
                    url = url.substringAfter("'").substringBeforeLast("'") + "&locked=true"
                }
            }
        }

        return allChapters.filter { !it.url.startsWith("javascript") }
            .sortedByDescending { it.chapter_number }
    }

    override fun chapterListSelector() =
        "section.section_chapters div.o_sortable > a.o_chapter-container, " +
            "section.section_chapters div.o_sortable div.o_chapter-vol-container tr.o_chapter a.o_chapter-container.pad-r-0"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val isVolume = element.select("div:nth-child(1) table").first() == null

        if (isVolume) {
            name = element.text()
        } else {
            val leftSide = element.select("div:nth-child(1) table").first()!!
            val rightSide = element.select("div:nth-child(2) table").first()!!

            name = rightSide.select("td").first()!!.text()
            date_upload = leftSide.select("td[align=right]").first()!!.text().toDate()
        }

        chapter_number = name.substringAfter("Ch. ").toFloatOrNull() ?: -1F
        scanlator = "VIZ Media"
        url = element.attr("data-target-url")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaUrl = chapter.url
            .substringBefore("-chapter")
            .replace("jump/", "jump/chapters/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageCount = document.select("script:containsData(var pages)").first().data()
            .substringAfter("= ")
            .substringBefore(";")
            .toInt()
        val mangaId = document.location()
            .substringAfterLast("/")
            .substringBefore("?")

        return IntRange(0, pageCount)
            .map {
                val imageUrl = "$baseUrl/manga/get_manga_url".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("device_id", "3")
                    .addQueryParameter("manga_id", mangaId)
                    .addQueryParameter("page", it.toString())
                    .addEncodedQueryParameter("referer", document.location())
                    .toString()

                Page(it, imageUrl)
            }
    }

    override fun imageUrlRequest(page: Page): Request {
        val url = page.url.toHttpUrlOrNull()!!
        val referer = url.queryParameter("referer")!!
        val newUrl = url.newBuilder()
            .removeAllEncodedQueryParameters("referer")
            .toString()

        val newHeaders = headersBuilder()
            .add("X-Client-Login", (loggedIn ?: false).toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Referer", referer)
            .build()

        return GET(newUrl, newHeaders)
    }

    override fun imageUrlParse(response: Response): String {
        val cdnUrl = response.body!!.string()
        val referer = response.request.header("Referer")!!

        return cdnUrl.toHttpUrlOrNull()!!.newBuilder()
            .addEncodedQueryParameter("referer", referer)
            .toString()
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!.toHttpUrlOrNull()!!
        val referer = imageUrl.queryParameter("referer")!!
        val newImageUrl = imageUrl.newBuilder()
            .removeAllEncodedQueryParameters("referer")
            .toString()

        val newHeaders = headersBuilder()
            .set("Referer", referer)
            .build()

        return GET(newImageUrl, newHeaders)
    }

    private fun authCheckIntercept(chain: Interceptor.Chain): Response {
        if (loggedIn == null) {
            val refreshHeaders = headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            val loginCheckRequest = GET("$baseUrl/$REFRESH_LOGIN_LINKS_URL", refreshHeaders)
            val loginCheckResponse = chain.proceed(loginCheckRequest)
            val document = loginCheckResponse.asJsoup()

            loggedIn = document.select("div#o_account-links-content").first()!!
                .attr("logged_in")!!.toBoolean()

            loginCheckResponse.close()
        }

        return chain.proceed(chain.request())
    }

    private fun authChapterCheckIntercept(chain: Interceptor.Chain): Response {
        val requestUrl = chain.request().url.toString()

        if (!requestUrl.contains("/chapter/") || !requestUrl.contains("&locked=true")) {
            return chain.proceed(chain.request())
        }

        val mangaId = requestUrl.substringAfterLast("/").substringBefore("?")

        val authCheckHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("X-Client-Login", (loggedIn ?: false).toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val authCheckUrl = "$baseUrl/$MANGA_AUTH_CHECK_URL".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("device_id", "3")
            .addQueryParameter("manga_id", mangaId)
            .toString()
        val authCheckRequest = GET(authCheckUrl, authCheckHeaders)
        val authCheckResponse = chain.proceed(authCheckRequest)
        val authCheckJson = JsonParser.parseString(authCheckResponse.body!!.string()).obj

        authCheckResponse.close()

        if (authCheckJson["ok"].bool && authCheckJson["archive_info"]["ok"].bool) {
            val newChapterUrl = chain.request().url.newBuilder()
                .removeAllQueryParameters("locked")
                .build()
            val newChapterRequest = chain.request().newBuilder()
                .url(newChapterUrl)
                .build()

            return chain.proceed(newChapterRequest)
        }

        if (
            authCheckJson["archive_info"]["err"].nullObj != null &&
            authCheckJson["archive_info"]["err"]["code"].int == 4 &&
            loggedIn == true
        ) {
            throw Exception(SESSION_EXPIRED)
        }

        throw Exception(AUTH_CHECK_FAILED)
    }

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)!!.time
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
        }

        private const val COUNTRY_NOT_SUPPORTED = "Your country is not supported, try using a VPN."
        private const val SESSION_EXPIRED = "Your session has expired, please log in through WebView again."
        private const val AUTH_CHECK_FAILED = "Something went wrong in the auth check."

        private const val REFRESH_LOGIN_LINKS_URL = "account/refresh_login_links"
        private const val MANGA_AUTH_CHECK_URL = "manga/auth"
    }
}
