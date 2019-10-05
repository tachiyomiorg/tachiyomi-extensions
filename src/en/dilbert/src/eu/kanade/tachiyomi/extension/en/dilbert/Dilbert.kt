package eu.kanade.tachiyomi.extension.en.dilbert

import android.os.Build.VERSION
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import okhttp3.Headers
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Dilbert : ParsedHttpSource() {

    override val name = "Dilbert"

    override val baseUrl = "https://dilbert.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .addNetworkInterceptor(RateLimitInterceptor(4)).build()

    private val userAgent = "Mozilla/5.0 " +
        "(Android ${VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${BuildConfig.VERSION_NAME}"

    private val dateFormat = SimpleDateFormat("EEEE MMMM dd, yyyy", Locale.US)

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", userAgent)
        add("Referer", baseUrl)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return Observable.just(MangasPage((currentYear downTo 1989).map {
            SManga.create().apply {
                url = "?$it"
                title = "$name ($it)"
                artist = "Scott Adams"
                author = "Scott Adams"
                status = if (it < currentYear) SManga.COMPLETED else SManga.ONGOING
                description = """
                A satirical comic strip featuring Dilbert, a competent, but seldom recognized engineer.
                (This entry includes all the chapters published in $it.)
                """.trimIndent()
                thumbnail_url = "https://dilbert.com/assets/favicon/favicon-196x196-cf4d86b485e628a034ab8b961c1c3520b5969252400a80b9eed544d99403e037.png"
            }
        }, false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga) =
        Observable.just(manga.apply { initialized = true })

    private fun chapterListRequest(manga: SManga, page: Int = 1) =
        GET("$baseUrl/search_results?sort=date_asc&year=${manga.year}&page=$page", headers)

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val date = element.first(".comic-title-date").text()
        url = element.first(".img-comic-link").attr("href")
        name = element.first(".comic-title-name").text().ifBlank { date }
        date_upload = dateFormat.parse(date).time
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        fun getChapters(page: Int = 1): Document {
            val res = client.newCall(chapterListRequest(manga, page)).execute()
            if (!res.isSuccessful) {
                res.close()
                throw Exception("HTTP error ${res.code()}")
            }
            return res.asJsoup().also {
                chapters.addAll(it.select(".comic-item").map(::chapterFromElement))
            }
        }
        val pages = getChapters().first(".pagination > li:nth-last-child(2) > a").text().toInt()
        for (page in 2..pages) getChapters(page)
        return Observable.just(
            chapters.sortedBy(SChapter::date_upload).mapIndexed {
                i, ch -> ch.apply { chapter_number = i + 1f }
            }.reversed()
        )
    }

    override fun fetchPageList(chapter: SChapter) =
        Observable.just(listOf(Page(0, chapter.url)))

    override fun imageUrlRequest(page: Page) = GET(page.url, headers)

    override fun imageUrlParse(document: Document) =
        "https:" + document.first(".img-comic").attr("src")

    private val SManga.year: Int
        get() = url.substringAfterLast('?').toInt()

    private fun Element.first(selector: String) = select(selector).first()

    override fun popularMangaSelector() = ""

    override fun popularMangaNextPageSelector() = ""

    override fun searchMangaSelector() = ""

    override fun searchMangaNextPageSelector() = ""

    override fun latestUpdatesSelector() = ""

    override fun latestUpdatesNextPageSelector() = ""

    override fun chapterListSelector() = ""

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun chapterListRequest(manga: SManga) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun mangaDetailsParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun pageListParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun popularMangaFromElement(element: Element) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaFromElement(element: Element) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException("This method should not be called!")
}
