package eu.kanade.tachiyomi.extension.en.dilbert

import android.os.Build.VERSION
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar
import java.text.SimpleDateFormat

class Dilbert : ParsedHttpSource() {

    override val name = "Dilbert"

    override val baseUrl = "https://dilbert.com"

    override val lang = "en"

    override val supportsLatest = false

    private val userAgent = "Mozilla/5.0 " +
        "(Android ${VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${BuildConfig.VERSION_NAME}"

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", userAgent)
        add("Referer", baseUrl)
    }

    override fun fetchPopularManga(page: Int) = Observable.just(
        MangasPage(listOf(SManga.create().apply {
            url = baseUrl
            title = name
            status = SManga.ONGOING
            artist = "Scott Adams"
            author = "Scott Adams"
            description = """
            A satirical comic strip featuring Dilbert, a competent, but seldom recognized engineer.

            NOTE: there are over 11000 chapters so the first load may take a while.
            """.trimIndent()
            thumbnail_url = "https://dilbert.com/assets/favicon/favicon-196x196-cf4d86b485e628a034ab8b961c1c3520b5969252400a80b9eed544d99403e037.png"
        }), false)
    )

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga) = Observable.just(manga)

    // this sucks but it's the only way to avoid sending thousands of requests
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val now = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        val start = Calendar.getInstance().apply { set(1989, 3, 16) }
        val shortFormat = SimpleDateFormat("yyyy-MM-dd")
        val longFormat = SimpleDateFormat("EEEE MMMM dd, yyyy")
        val result = ArrayList<SChapter>(now - start)
        while(start.before(now)) {
            result.add(SChapter.create().apply {
                url = "$baseUrl/strip/${shortFormat.format(start.time)}"
                chapter_number = result.size + 1f
                name = longFormat.format(start.time)
                date_upload = start.timeInMillis
            })
            start.add(Calendar.DAY_OF_MONTH, 1)
        }
        return Observable.just(result.sortedByDescending(SChapter::chapter_number))
    }

    override fun fetchPageList(chapter: SChapter) =
        Observable.just(listOf(Page(0, chapter.url)))

    override fun imageUrlRequest(page: Page) = GET(page.url, headers)

    // the src doesn't contain the protocol
    override fun imageUrlParse(document: Document) =
        "https:" + document.select(".img-comic").attr("src")

    operator fun Calendar.minus(other: Calendar) =
        Math.ceil((this.timeInMillis - other.timeInMillis) / 8.64e7).toInt()

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

    override fun chapterFromElement(element: Element) =
        throw UnsupportedOperationException("This method should not be called!")
}
