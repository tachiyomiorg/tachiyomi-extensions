package eu.kanade.tachiyomi.extension.en.xkcd

import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
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
        manga.thumbnail_url = "https://xkcd.com/s/0b7742.png"

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

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
        var jsonData = response.asJsoup().text()
        val json = JsonParser().parse(jsonData).asJsonObject
        val imageUrl = json["img"].string
        val pages = mutableListOf<Page>()
        pages.add(Page(0, "", imageUrl))
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

}
