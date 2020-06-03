package eu.kanade.tachiyomi.extension.en.schlockmercenary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.lang.UnsupportedOperationException
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Schlockmercenary : ParsedHttpSource() {

    override val name = "Schlock Mercenary"

    override val baseUrl = "https://www.schlockmercenary.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.empty()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListSelector() = "ul.chapters > li > a"

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    // This is what gets the pages
    override fun pageListParse(document: Document): List<Page> {
        return listOf()
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/archives")

    override fun popularMangaSelector(): String = "div.archive-book"

    override fun popularMangaFromElement(element: Element): SManga {
        val book = element.select("h4 > a").first()
        return SManga.create().apply {
            url = book.attr("href")
            title = book.text()
            artist = "Howard Tayler"
            author = "Howard Tayler"
            // I'm not sure how to determine this quite yet, so I'll just hardcode the latest one to get something done
//            status = if (book.text().contains("Book 20")) SManga.ONGOING else SManga.COMPLETED
            status = SManga.UNKNOWN
            description = element.select("p").first().text()
            thumbnail_url = element.select("img").first().attr("src")
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun searchMangaSelector(): String = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException("Not used")
}
