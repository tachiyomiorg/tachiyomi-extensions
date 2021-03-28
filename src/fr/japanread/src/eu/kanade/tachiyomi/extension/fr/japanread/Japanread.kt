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
import java.util.Calendar

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
            thumbnail_url = element.select("img").attr("src").replace("manga_small", "manga_large")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "section.main-content > .container > .row > .col-lg-9 tbody > tr > td[rowspan]"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.nextElementSibling().nextElementSibling().select("a").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("src").replace("manga_medium", "manga_large")
        }
    }

    override fun latestUpdatesNextPageSelector() = "a[rel=\"next\"]"

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
            title = document.select("h1.card-header").text()
            artist = document.select("div.col-lg-3:contains(Artiste) + div").text()
            author = document.select("div.col-lg-3:contains(Auteur) + div").text()
            description = document.select("div.col-lg-3:contains(Description) + div").text()
            genre = document.select("div.col-lg-3:contains(Type - Catégories) + div .badge").joinToString { it.text() }
            status = document.select("div.col-lg-3:contains(Statut) + div").text().let {
                when {
                    it.contains("En cours") -> SManga.ONGOING
                    it.contains("Terminé") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = document.select("img[alt=\"couverture manga\"]").attr("src")
        }
    }

    // Chapters
    override fun chapterListSelector() = "#chapters div[data-row=\"chapter\"]"

    // Subtract relative date
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringAfter("Il y a").trim().split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "ans" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "an" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "mois" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "sem." -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "j" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "h" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "min" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "s" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("div.col-lg-5 a").text()
            setUrlWithoutDomain(element.select("div.col-lg-5 a").attr("href"))
            date_upload = parseRelativeDate(element.select("div.order-lg-8").text())
            scanlator = element.select(".chapter-list-group a").joinToString { it.text() }
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
