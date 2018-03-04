package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class DynastyChapters : DynastyScans() {
    override val name = "Dynasty-Chapters"
    override fun popularMangaInitialUrl() = ""

    private fun popularMangaInitialUrl(page: Int) = "$baseUrl/search?q=&classes%5B%5D=Chapter&page=$page=$&sort="

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&classes%5B%5D=Chapter&sort=", headers)
    }


    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img")[2].absUrl("src")
        return manga
    }

    override fun searchMangaSelector() = "dd"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleSelect = element.select("a.name")
        manga.setUrlWithoutDomain(titleSelect.attr("href"))
        manga.title = titleSelect.text()
        val artistAuthorElements = element.select("a")
        if (!artistAuthorElements.isEmpty()) {
            if (artistAuthorElements.lastIndex == 1) {
                manga.author = artistAuthorElements[1].text()
            } else {
                manga.artist = artistAuthorElements[1].text()
                manga.author = artistAuthorElements[2].text()
            }
        }

        val genreElements = element.select("a.label")
        parseGenres(genreElements, manga)
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(chapterListSelector()).map {
            chapterFromElement(it)
        }
    }

    override fun chapterListSelector() = ".chapters.show#main"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.baseUri())
        chapter.name = element.select("h3").text()
        chapter.date_upload = element.select("span.released")?.first().let {
            SimpleDateFormat("MMM dd, yy", Locale.ENGLISH).parse(it!!.text()).time
        }
        return chapter
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(popularMangaInitialUrl(page), headers)
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

}