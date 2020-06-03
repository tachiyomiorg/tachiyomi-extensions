package eu.kanade.tachiyomi.extension.en.schlockmercenary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Schlockmercenary : ParsedHttpSource() {

    override val name = "Schlock Mercenary"

    override val baseUrl = "https://www.schlockmercenary.com"

    override val lang = "en"

    override val supportsLatest = true

    val books = listOf<MangasPage>()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("/archives")
        manga.title = "xkcd"
        manga.artist = "Randall Munroe"
        manga.author = "Randall Munroe"
        manga.status = SManga.ONGOING
        manga.description = "A webcomic of romance, sarcasm, math and language"
        manga.thumbnail_url = thumbnailUrl

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.empty()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListSelector() = "ul.chapters > li > a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.name = element.text()
        if (chapter.name.contains("prologue(s)", true)) {
            chapter.chapter_number = 0F
        } else if (chapter.name.contains("prologue", true)) {
            chapter.chapter_number = 0.1F
        } else if (chapter.name.contains("epilogue", true)) {
            chapter.chapter_number = 1000F
        } else {
            val number = e
            chapter.chapter_number =
        }
        chapter.chapter_number = number.toFloat()
    }

    // This is what gets the pages
    override fun pageListParse(document: Document): List<Page> {
        return listOf()
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/archives")

    override fun popularMangaSelector(): String = "div.archive-book"

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")

    companion object {
        const val thumbnailUrl = "https://fakeimg.pl/550x780/ffffff/6E7B91/?text=xkcd&font=museo"
        const val baseAltTextUrl = "https://fakeimg.pl/1500x2126/ffffff/000000/?text="
        const val baseAltTextPostUrl = "&font_size=42&font=museo"
    }
}
