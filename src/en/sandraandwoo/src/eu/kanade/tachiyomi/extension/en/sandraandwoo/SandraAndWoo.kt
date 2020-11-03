package eu.kanade.tachiyomi.extension.en.sandraandwoo

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

class SandraAndWoo : ParsedHttpSource() {
    override val name = "Sandra and Woo"
    override val baseUrl = "http://www.sandraandwoo.com"
    override val lang = "en"
    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("/archive/")
        manga.title = "Sandra and Woo"
        manga.artist = "Puri (Powree) Andini"
        manga.author = "Oliver (Novil) Knörzer"
        manga.status = SManga.ONGOING
        manga.description = "Sandra and Woo is a comedy comic strip featuring the 13-year-old girl Sandra North and her mischievous pet raccoon Woo"
        manga.thumbnail_url = thumbnailUrl

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = Observable.just(MangasPage(arrayListOf(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListSelector() = "td.archive-title a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val rawUrl = element.attr("href")
        chapter.url = rawUrl.replace("http://www.sandraandwoo.com", "")
        val number = try { element.text().split(" ")[0].replace("[", "").replace("]", "").toFloat() } catch (e: NumberFormatException) {0.0f}
        chapter.chapter_number = number
        chapter.name = element.text()
        val year = rawUrl.split("/")[3]
        val month = rawUrl.split("/")[4]
        val day = rawUrl.split("/")[5]
        chapter.date_upload = SimpleDateFormat("yyyy-MM-dd").parse("$year-$month-$day").time
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val src = document.select("#comic img").first().attr("src")

        val pages = mutableListOf<Page>()
        pages.add(Page(0, "", baseUrl + src))

        return pages
    }

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

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    companion object {
        const val thumbnailUrl = "http://www.sandraandwoo.com/10years/sandra-and-woo-10-years-cover-e3jDcipN.png"
    }

}
