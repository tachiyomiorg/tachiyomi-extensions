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

class DynastyOneshot : DynastyScans() {

    override val name = "Dynasty- Oneshot"

    override fun popularMangaInitialUrl() = "$baseUrl/search?q=&with%5B%5D=5102&sort="

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&with%5B%5D=5102&sort=", headers)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create()

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

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

}