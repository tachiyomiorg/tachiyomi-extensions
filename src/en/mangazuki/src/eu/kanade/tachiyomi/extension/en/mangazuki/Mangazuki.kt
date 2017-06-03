package eu.kanade.tachiyomi.extension.en.mangazuki
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class Mangazuki : ParsedHttpSource() {

    override val name = "Mangazuki"

    override val baseUrl = "https://mangazuki.co"

    override val lang = "en"

    override val supportsLatest = true

    private val datePattern = Pattern.compile("(\\d+)\\s([a-z]*?)s?\\s")

    private val dateFormat = SimpleDateFormat("MMM d yyyy", Locale.ENGLISH)

    private val dateFields = mutableMapOf(
            Pair("second", Calendar.SECOND),
            Pair("minute", Calendar.MINUTE),
            Pair("hour", Calendar.HOUR),
            Pair("day", Calendar.DATE),
            Pair("week", Calendar.WEEK_OF_YEAR),
            Pair("month", Calendar.MONTH),
            Pair("year", Calendar.YEAR)
    )

    override fun popularMangaSelector() = "div.caption > h6"

    override fun latestUpdatesSelector() = "div.caption > h6"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest?page=$page", headers)
    }

    private fun mangaFromElement(query: String, element: Element): SManga {
        val manga = SManga.create()
        element.select(query).first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return mangaFromElement("a", element)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li.next > a[rel=next]"

    override fun latestUpdatesNextPageSelector() = "ul.pagination > li.next > a[rel=next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/series?q=$query&page=$page").newBuilder()

        return GET(url.toString(), headers)
    }
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return mangaFromElement("a", element)
    }

    override fun searchMangaNextPageSelector() = "ul.pagination > li.next > a[rel=next]"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("#activity > div > div.panel > div.panel-body")

        val manga = SManga.create()
        manga.author = "Unknown"
        manga.artist = "Unknown"
        manga.genre = "Unknown"
        manga.description = infoElement.select("p").text()
        manga.status = 0
        manga.thumbnail_url = baseUrl + infoElement.select("div.media > div.media-left img").attr("src")
        return manga
    }

    override fun chapterListSelector() = "div#activity > div > div > ul > li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a.media-link")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("div.media-body > h6.media-heading").text()
        chapter.date_upload = element.select("div.media-body > span").first().let {
            parseDateFromElement(it)
        }
        return chapter
    }

    private fun parseDateFromElement(dateElement: Element): Long {
        val dateAsString = dateElement.text().filterNot { it == ','}

        var date: Date
        try {
            date = dateFormat.parse(dateAsString.substringAfter("Added on "))
        } catch (e: ParseException) {
            val m = datePattern.matcher(dateAsString)
            if (m.find()) {
                val cal = Calendar.getInstance()
                do {
                    val amount = m.group(1).toInt()
                    val unit = m.group(2)

                    cal.add(dateFields[unit]!!, -amount)
                } while (m.find())
                date = cal.time;
            } else {
                return 0
            }
        }

        return date.time
    }

    private fun chapterNextPageSelector() = "ul.pagination > li.next > a[rel=next]"

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        val urlBase = response.request().url().toString()
        var document = response.asJsoup()

        while (true) {
            val pageChapters = document.select(chapterListSelector()).map { chapterFromElement(it) }
            if (pageChapters.isEmpty())
                break

            allChapters += pageChapters

            val hasNextPage = document.select(chapterNextPageSelector()).isNotEmpty()
            if (!hasNextPage)
                break

            val nextUrl = urlBase + "?page=${++page}"
            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        return allChapters
    }

    override fun pageListParse(document: Document) = document.select("div.row > img").mapIndexed { i, element -> Page(i, "", element.attr("src")) }

    override fun imageUrlParse(document: Document) = ""
}