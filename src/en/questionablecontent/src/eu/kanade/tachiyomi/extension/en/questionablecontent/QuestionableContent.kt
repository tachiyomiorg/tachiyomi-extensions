package eu.kanade.tachiyomi.extension.en.questionablecontent

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class QuestionableContent : ParsedHttpSource() {

    override val name = "Questionable Content"

    override val baseUrl = "https://www.questionablecontent.net"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "Questionable Content"
            artist = "Jeph Jacques"
            author = "Jeph Jacques"
            status = SManga.ONGOING
            url = "/archive.php"
            description = "An internet comic strip about romance and robots"
            thumbnail_url = "https://i.ibb.co/ZVL9ncS/qc-teh.png"
        }

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = Observable.just(MangasPage(arrayListOf(), false))

    override fun fetchMangaDetails(manga: SManga) = Observable.just(manga)

    override fun chapterListSelector() = """div#container a[href^="view.php?comic="]"""

    override fun chapterFromElement(element: Element): SChapter {
        val urlregex = """view\.php\?comic=(.*)""".toRegex()
        val chapterUrl = element.attr("href")
        val number = urlregex.find(chapterUrl)!!.groupValues[1]

        return SChapter.create().apply {
            url = chapterUrl
            chapter_number = number.toFloat()
            name = element.text()
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val pages = mutableListOf<Page>()
        pages.add(Page(0, "", "$baseUrl/comics/${chapter.chapter_number.toInt()}.png"))
        return Observable.just(pages)
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

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
