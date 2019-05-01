package eu.kanade.tachiyomi.extension.en.xkcd

import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }


    override fun chapterListSelector() = "div#middleContainer.box a"

    override fun chapterFromElement(element: Element): SChapter {

        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        val number = chapter.url.removeSurrounding("/")
        chapter.chapter_number = number.toFloat()
        chapter.name = number + " - " + element.text()
        chapter.date_upload = element.attr("title").let {
            SimpleDateFormat("yyyy-MM-dd").parse(it).time
        }
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url + "info.0.json")

    override fun pageListParse(response: Response): List<Page> {
        var jsonData = response.body()!!.string()
        jsonData = jsonData.replace("\\u00e2\\u0080\\u0094", "\\u2014")
                .replace("\\u00c3\\u00a9", "\\u00e9")
                .replace("\\u00e2\\u0080\\u0093", "\\u2014")
                .replace("\\u00c3\\u00b3", "\\u00F3")
                .replace("#", "%23")
                .replace("&eacute;", "\\u00e9")
        val json = JsonParser().parse(jsonData).asJsonObject

        //the comic get hd if  1084 or higher
        var imageUrl = json["img"].string
        val number = json["num"].int
        if (number >= 1084) {
            imageUrl = imageUrl.replace(defaultExt, comicsAfter1084Ext)
        }
        val pages = mutableListOf<Page>()
        pages.add(Page(0, "", imageUrl))

        //create a text image for the alt text
        var titleWords = json["title"].string.splitToSequence(" ")
        var altTextWords = json["alt"].string.splitToSequence(" ")

        var builder = StringBuilder()
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

        pages.add(Page(1, "", baseAltTextUrl + builder.toString() + baseAltTextPostUrl))
        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

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
        const val comicsAfter1084Ext = "_2x.png"
        const val defaultExt = ".png"
    }

}
