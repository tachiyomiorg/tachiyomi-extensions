package eu.kanade.tachiyomi.extension.en.questionablecontent

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class QuestionableContent : ParsedHttpSource() {
    override val name = "Questionable Content"

    override val versionId = 1

    override val baseUrl = "https://www.questionablecontent.net"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.title = "Questionable Content"
        manga.artist = "Jeph Jacques"
        manga.author = "Jeph Jacques"
        manga.status = SManga.ONGOING
        manga.url = "/archive.php"
        manga.description = "An internet comic strip about romance and robots"
        manga.thumbnail_url = "https://i.ibb.co/ZVL9ncS/qc-teh.png"
        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = Observable.just(MangasPage(arrayListOf(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListSelector() = """div#container a[href^="view.php?comic="]"""

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlregex = """view\.php\?comic=(.*)""".toRegex()
        val number = urlregex.find(element.attr("href"))!!.groupValues[1]
        chapter.chapter_number = number.toFloat()
        chapter.name = element.text()
        return chapter
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
