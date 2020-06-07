package eu.kanade.tachiyomi.extension.en.killsixbilliondemons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.util.LinkedList
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

/**
 *  @author Aria Moradi <aria.moradi007@gmail.com>
 */

class KillSixBillionDemons : HttpSource() {

    override val name = "KillSixBillionDemons"

    override val baseUrl = "https://killsixbilliondemons.com"

    override val lang = "en"

    override val supportsLatest: Boolean = false

    // list of books

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl)
    }

    fun popularMangaSelector(): String {
        return "#chapter option:contains(book)"
    }

    fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text().substringBefore(" (")
            thumbnail_url = "https://dummyimage.com/768x994/000/ffffff.jpg&text=$title"
            artist = "Abbadon"
            author = "Abbadon"
            status = SManga.UNKNOWN
            url = title // this url is not useful at all but must set to something unique or the app breaks!
        }
    }

    // latest Updates not used

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    // books dont change around here

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)
//    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
//        return client.newCall(popularMangaRequest(1))
//            .asObservableSuccess()
//            .map { response ->
//                popularMangaParse(response).mangas.find { manga.title == it.title }
//            }
//    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }

    override fun chapterListRequest(manga: SManga): Request = popularMangaRequest(1)

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()

        val options = document.select(chapterListSelector())

        var chapters = LinkedList<SChapter>()
        var bookNum = 0
        var targetBookNum = manga.title.split(":")[0].split(" ")[1].toInt()

        for (element in options) {
            val text = element.text()
            if (text.startsWith("Book")) {
                bookNum += 1
                continue
            }
            if (bookNum > targetBookNum)
                break

            if (bookNum == targetBookNum) {
                chapters.add(
                    SChapter.create().apply {
                        url = element.attr("value")

                        val textSplit = text.split(" ")

                        name = "Chpater ${textSplit[0]}"
                    }
                )
            }
        }

        return chapters.reversed()
    }

    fun chapterListSelector(): String {
        return "#chapter option"
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val wordpressPages = mutableListOf<Document>()
        // get the first page
        val firstPageURL = chapter.url + "?order=ASC" // change the url to ask Wordpress to reverse the posts

        val firstPage = Jsoup.connect(firstPageURL).get()
        wordpressPages.add(firstPage)

        val otherPages = firstPage.select("#paginav a")

        for (i in 0 until (otherPages.size - 1)) // ignore the last one (last page button)
            wordpressPages.add(Jsoup.connect(otherPages[i].attr("href")).get())

        val chapterPages = mutableListOf<Page>()
        var pageNum = 1

        wordpressPages.forEach {
            it.select(".post-content .entry a:has(img)").forEach {
                chapterPages.add(
                    Page(pageNum, it.attr("href"), it.select("img").attr("src"))
                )
                pageNum++
            }
        }

        return Observable.just(chapterPages)
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
}
