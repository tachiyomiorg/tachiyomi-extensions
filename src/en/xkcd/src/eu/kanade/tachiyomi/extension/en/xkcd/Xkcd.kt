package eu.kanade.tachiyomi.extension.en.xkcd

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

class Xkcd : ParsedHttpSource() {

    override val name = "xkcd"

    override val baseUrl = "https://xkcd.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("/archive")
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

    override fun chapterListSelector() = "div#middleContainer.box a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        val number = chapter.url.removeSurrounding("/")
        chapter.chapter_number = number.toFloat()
        chapter.name = number + " - " + element.text()
        chapter.date_upload = element.attr("title").let {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it).time
        }
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val titleWords: Sequence<String>
        val altTextWords: Sequence<String>

        // transforming filename from info.0.json isn't guaranteed to work, stick to html
        // if an HD image is available it'll be the srcset attribute
        val image = document.select("div#comic img").let {
            if (it.hasAttr("srcset")) it.attr("abs:srcset").substringBefore(" ")
                else it.attr("abs:src")
        }

        // create a text image for the alt text
        document.select("div#comic img").let {
            titleWords = it.attr("alt").splitToSequence(" ")
            altTextWords = it.attr("title").splitToSequence(" ")
        }

        val builder = StringBuilder()
        var count = 0

        for (i in titleWords) {
            if (count != 0 && count.rem(7) == 0) {
                builder.append("%0A")
            }
            builder.append(i).append("+")
            count++
        }
        builder.append("%0A%0A")

        var charCount = 0

        for (i in altTextWords) {
            if (charCount > 25) {
                builder.append("%0A")
                charCount = 0
            }
            builder.append(i).append("+")
            charCount += i.length + 1
        }

        return listOf(Page(0, "", image), Page(1, "", baseAltTextUrl + builder.toString() + baseAltTextPostUrl))
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

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
