package eu.kanade.tachiyomi.extension.en.schlockmercenary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Schlockmercenary : ParsedHttpSource() {

    override val name = "Schlock Mercenary"

    override val baseUrl = "https://www.schlockmercenary.com"

    override val lang = "en"

    override val supportsLatest = false

    var chapter_count = 1

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun popularMangaRequest(page: Int): Request = GET("${baseUrl}$archiveUrl")

    override fun popularMangaSelector(): String = "div.archive-book"

    override fun popularMangaFromElement(element: Element): SManga {
        val book = element.select("h4 > a").first()
        val thumb = (baseUrl + (element.select("img").first()?.attr("src")
            ?: defaultThumbnailUrl)).substringBefore("?")
        return SManga.create().apply {
            url = book.attr("href")
            title = book.text()
            artist = "Howard Tayler"
            author = "Howard Tayler"
            // Schlock Mercenary finished as of July 2020
            status = SManga.COMPLETED
            description = element.select("p").first()?.text() ?: ""
            thumbnail_url = thumb
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val requestUrl = "${baseUrl}$archiveUrl"
        return client.newCall(GET(requestUrl))
            .asObservableSuccess()
            .map { response ->
                selectChaptersFromBook(response, manga)
            }
    }

    private fun selectChaptersFromBook(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()
        val sanitizedTitle = manga.title.replace("""([",'])""".toRegex(), "\\\\$1")
        val book = document.select(popularMangaSelector() + ":contains($sanitizedTitle)")
        val chapters = mutableListOf<SChapter>()
        chapter_count = 1
        book.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        return chapters
    }

    override fun chapterListSelector() = "ul.chapters > li > a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.name = element.text()
        chapter.chapter_number = chapter_count++.toFloat()
        chapter.date_upload = chapter.url.takeLast(10).let {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it)!!.time
        }
        return chapter
    }

    // This is what gets the pages
    override fun pageListParse(document: Document): List<Page> {
        return listOf()
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector(): String? = null

    override fun searchMangaSelector(): String = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException("Not used")

    companion object {
        const val defaultThumbnailUrl = "/static/img/logo.b6dacbb8.jpg"
        const val archiveUrl = "/archives/"
    }
}
