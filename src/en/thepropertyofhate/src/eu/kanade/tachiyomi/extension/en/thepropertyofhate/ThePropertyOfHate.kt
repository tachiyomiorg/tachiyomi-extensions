package eu.kanade.tachiyomi.extension.en.thepropertyofhate

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 *  @author Aria Moradi <aria.moradi007@gmail.com>
 */

class ThePropertyOfHate : HttpSource() {

    override val name = "The Property of Hate"

    override val baseUrl = "http://jolleycomics.com"

    val firstChapterUrl = "/TPoH/The Hook/"

    override val lang = "en"

    override val supportsLatest: Boolean = false

    // the one and only manga entry
    fun manga(): SManga {
        return SManga.create().apply {
            title = "The Property of Hate"
            thumbnail_url = "https://pbs.twimg.com/media/DOBCcMiWkAA8Hvu.jpg"
            artist = "Sarah Jolley"
            author = "Sarah Jolley"
            status = SManga.UNKNOWN
            url = baseUrl
        }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(manga()), false))
    }

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    // latest Updates not used

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    // the manga is one and only, but still write the data again to avoid bugs in backup restore
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga())
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    // chapter list

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + firstChapterUrl, headers) // no real base url for this comic so must read the first chapter's link
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapters = mutableListOf<SChapter>(
            SChapter.create().apply() { // must hard code the first one
                url = firstChapterUrl
                name = "The Hook"
            }
        )

        document.select("select > option").forEach { option ->
            if (!option.text().startsWith("-")) // ignore "jump to entry" option
                chapters.add(
                    SChapter.create().apply {
                        url = option.attr("value")
                        name = option.text()
                    }
                )
        }

        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // parse the options for this chapter to get page links
        val pages = document.select("select > optgroup > option").mapIndexed { pageNum, option ->
            Page(pageNum, baseUrl + option.attr("value"))
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()

        return baseUrl + document.select(".comic_comic > img").first().attr("src")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Search functionality is not available.")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
}
