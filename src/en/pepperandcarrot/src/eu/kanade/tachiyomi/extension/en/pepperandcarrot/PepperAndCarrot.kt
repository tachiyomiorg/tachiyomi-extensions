package eu.kanade.tachiyomi.extension.en.pepperandcarrot

import com.github.salomonbrys.kotson.string
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

class PepperAndCarrot : ParsedHttpSource() {
    override val name = "Pepper and Carrot"
    override val baseUrl = "https://www.peppercarrot.com/"
    override val lang = "en"
    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("en/static3/webcomics")
        manga.title = "Pepper and Carrot"
        manga.artist = "David Revoy"
        manga.author = "David Revoy"
        manga.status = SManga.ONGOING
        manga.description = "Pepper&Carrot is a comedy/humor webcomic suited for everyone, every age. No mature content, no violence. Free(libre) and open-source, Pepper&Carrot is a proud example of how cool free-culture can be."
        manga.thumbnail_url = thumbnailUrl

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = Observable.just(MangasPage(arrayListOf(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun chapterListSelector() = "figure.thumbnail"

    // Must be overriden, but not used since we overrode chapterListParse
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    /**
     * Override the chapter list parsing to enable certain elements to not match
     * (using a flatMap instead of a regular map)
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).flatMap { chapterListFromElement(it) }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    private fun chapterListFromElement(element: Element): List<SChapter> {
        try {
            val caption = element.select("figcaption");
            val a = element.select("a");

            val chapter = SChapter.create()
            chapter.url = a.attr("href").removePrefix(baseUrl);
            chapter.name = a.attr("title")
            val dateString = caption.text().split(",")[0]
            chapter.date_upload = SimpleDateFormat("yyyy/MM/dd").parse(dateString).time
            chapter.chapter_number = chapter.name.split(":")[0].split(" ")[1].toFloat()
            return listOf(chapter)
        } catch (e: Exception) {
            return listOf()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        var i = 0
        document.select(".comicpage").forEach {
            pages.add(Page(i, "", it.attr("src")))
            i++
        }

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
        const val thumbnailUrl = "https://fakeimg.pl/550x780/ffffff/6E7B91/?text=P&C&font=museo"
    }

}
