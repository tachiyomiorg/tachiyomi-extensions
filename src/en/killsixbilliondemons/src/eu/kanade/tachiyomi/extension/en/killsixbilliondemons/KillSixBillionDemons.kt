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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

/**
 *  @author Aria Moradi <aria.moradi007@gmail.com>
 */

open class KillSixBillionDemons : HttpSource() {

    override val name = "Kill Six Billion Demons"

    override val baseUrl = "https://killsixbilliondemons.com"

    override val lang = "en"

    // override to set it the the original id
    override val id = 5520021760150818261

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

    private fun popularMangaSelector(): String {
        return "#chapter option:contains(book)"
    }

    protected open fun popularMangaFromElement(element: Element): SManga {
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

    // Search functionality is not available

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    // books don't change around here, but still write the data again to avoid bugs in backup restore
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(popularMangaRequest(1))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response).mangas.find { manga.title == it.title }
            }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    // chapter list

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }

    override fun chapterListRequest(manga: SManga): Request = popularMangaRequest(1)

    private fun chapterListSelector(): String {
        return "#chapter option"
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()

        val options = document.select(chapterListSelector())

        val chapters = mutableListOf<SChapter>()
        var bookNum = 0
        val targetBookNum = manga.title.split(":")[0].split(" ")[1].toInt()

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

                        name = "Chapter ${textSplit[0]}"
                    }
                )
            }
        }

        return chapters.reversed()
    }

    // page list

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val wordpressPages = fetchChapterWordpressPagesList(chapter)

        val chapterPages = mutableListOf<Page>()

        wordpressPages.forEachIndexed { pageNum, wordpressPage ->
            wordpressPage.select(".post-content .entry a:has(img)").forEach { postImage ->
                chapterPages.add(
                    Page(pageNum, postImage.attr("href"), postImage.select("img").attr("src"))
                )
            }
        }

        return Observable.just(chapterPages)
    }

    fun fetchChapterWordpressPagesList(chapter: SChapter): MutableList<Document> {
        val wordpressPages = mutableListOf<Document>()
        // get the first page and add ir to the list
        val firstPageURL = chapter.url + "?order=ASC" // change the url to ask Wordpress to reverse the posts
        val firstPage = client.newCall(GET(firstPageURL)).execute().asJsoup()
        wordpressPages.add(firstPage)

        val otherPages = firstPage.select("#paginav a")

        for (i in 0 until (otherPages.size - 1)) // ignore the last one (last page button)
            wordpressPages.add(client.newCall(GET(otherPages[i].attr("href"))).execute().asJsoup())

        return wordpressPages
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    // image url: already handled

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")
}
