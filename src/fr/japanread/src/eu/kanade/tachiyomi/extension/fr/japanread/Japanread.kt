package eu.kanade.tachiyomi.extension.fr.japanread

import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Japanread : ParsedHttpSource() {

    override val name = "Japanread"

    override val baseUrl = "https://www.japanread.cc"

    override val lang = "fr"

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector() = "#nav-tabContent #nav-home li"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("p a").text()
            setUrlWithoutDomain(element.select("p a").attr("href"))
            thumbnail_url = element.select("img").attr("src").replace("manga_small", "manga_large").replace("manga_medium", "manga_large")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#nav-tabContent #nav-profile li"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = "#nav-tabContent #nav-profile li"

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query")
    }

    override fun searchMangaSelector() = "#manga-container > div > div"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("div.text-truncate a").text()
            setUrlWithoutDomain(element.select("div.text-truncate a").attr("href"))
            description = element.select("div.text-muted").text()
            thumbnail_url = element.select("img").attr("src").replace("manga_medium", "manga_large")
        }
    }

    override fun searchMangaNextPageSelector() = "a[rel=\"next\"]"

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h2[itemprop=\"name\"]").text()
            author = document.select("li[itemprop=\"author\"]").text()
            description = document.select("p[itemprop=\"description\"]").text()
            thumbnail_url = document.select(".contenu_fiche_technique .image_manga img").attr("src")
        }
    }

    // Chapters
    override fun chapterListSelector() = "#chapters .chapter-container div.row"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("div.col-lg-5 a").text()
            setUrlWithoutDomain(element.select("div.col-lg-5 a").attr("href"))
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val pageHeaders = headersBuilder().apply {
            add("x-requested-with", "XMLHttpRequest")
        }.build()
        return GET("$baseUrl/api/?id=$chapterId&type=chapter", pageHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject

        val baseImagesUrl = json["baseImagesUrl"].string

        return json["page_array"].asJsonArray.mapIndexed { idx, it ->
            val imgUrl = "$baseUrl$baseImagesUrl/${it.asString}"
            Page(idx, baseUrl, imgUrl)
        }
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not Used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
}
