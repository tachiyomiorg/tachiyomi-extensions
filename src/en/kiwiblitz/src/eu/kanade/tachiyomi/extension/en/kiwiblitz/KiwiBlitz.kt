package eu.kanade.tachiyomi.extension.en.kiwiblitz

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

class KiwiBlitz : ParsedHttpSource() {
    override val name = "KiwiBlitz"
    override val baseUrl = "http://www.kiwiblitz.com/"
    override val lang = "en"
    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("comic/archive/")
        manga.title = "Kiwi Blitz"
        manga.artist = "Mary Cagle"
        manga.author = "Mary Cagle"
        manga.status = SManga.ONGOING
        manga.description = "Kiwi Blitz is longform webcomic that chronicles the adventures of a couple teenagers’ attempts to fight crime in the not-too-distant future. Imagine an American superhero cartoon mixed with a Japanese mecha anime, and you’ve got the basic husk of the comic’s existence!"
        manga.thumbnail_url = thumbnailUrl

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = Observable.just(MangasPage(arrayListOf(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    // TODO This API is suspiciously like SMBC. I wonder if the sites use a common software and if we can abstract all clients for that software out somehow?
    override fun chapterListSelector() = "select[name='comic'] option"

    // Must be overriden, but not used since we overrode chapterListParse
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    /**
     * Override the chapter list parsing to enable certain elements to not match
     * (using a flatMap instead of a regular map)
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).reversed().flatMap { chapterListFromElement(it) }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    private fun chapterListFromElement(element: Element): List<SChapter> {
        try {
            val chapter = SChapter.create()
            chapter.url = element.attr("value")
            chapter.name = element.text().split(" - ")[1]
            val date = element.text().split(" - ")[0]
            val month = date.split(" ")[0]
            val day = date.split(", ")[0].split(" ")[1]
            val year = date.split(", ")[1]
            chapter.date_upload = SimpleDateFormat("yyyy-MMMMM-dd").parse("$year-$month-$day").time
            chapter.chapter_number = chapter.date_upload.toFloat()
            return listOf(chapter)
        } catch (e: Exception) {
            return listOf()
        }
    }

    private fun buildAltTextUrl(alttext: String): String {
        val titleWords = alttext.splitToSequence(" ")

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

        return baseAltTextUrl + builder.toString() + baseAltTextPostUrl
    }

    private fun isTitleTextInteresting(alttext: String): Boolean {
        return !(pageAltTextRegex.matches(alttext) or trackAltTextRegex.matches(alttext))
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val src = document.select("#cc-comicbody img").first().attr("src")
        pages.add(Page(0, "", src))

        try {
            val titletext = document.select("#cc-comicbody img").first().attr("title")
            if (isTitleTextInteresting(titletext)) {
                // Don't include the titletext if it's the date (YYYY-MM-DD) // EG for 2009-07-01
                pages.add(Page(1, "", buildAltTextUrl(titletext)))
            }
        } catch (e: Exception) {}

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
        const val thumbnailUrl = "https://fakeimg.pl/550x780/ffffff/6E7B91/?text=KiwiBlitz&font=museo"
        const val baseAltTextUrl = "https://fakeimg.pl/1500x2126/ffffff/000000/?text="
        const val baseAltTextPostUrl = "&font_size=42&font=museo"
        val pageAltTextRegex = Regex("Page\\s?\\d*")
        val trackAltTextRegex = Regex("Track\\s?\\d*\\s?[–\\-]\\s?\\d*") // TODO Note this isn't a regular dash
    }

}
