package eu.kanade.tachiyomi.extension.en.mangastream

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class Mangastream : ParsedHttpSource() {

    override val name = "Mangastream"

    override val baseUrl = "https://readms.net/"

    override val lang = "en"

    override val supportsLatest = true

    private val datePattern by lazy {
        Pattern.compile("(\\d+) days? ago")
    }

    override fun popularMangaSelector() = "table.table-striped > tbody > tr > td:nth-of-type(1)"

    override fun latestUpdatesSelector() = "div.col-sm-4 > div.side-nav > ul.new-list > li"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga", headers)

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("strong > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = if (it.hasAttr("title")) it.attr("title") else if (it.hasAttr("rel")) it.attr("rel") else it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            val name = it.attr("href").substringAfter("/r/").substringBefore("/")
            manga.setUrlWithoutDomain("http://mangastream.com/manga/$name")
            manga.title = it.html().substringBefore(" <strong>").substringAfterLast(">")
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/manga", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    private fun searchMangaSelector(query: String) = "table.table-striped > tbody > tr > td:nth-of-type(1):contains($query)"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector(query)).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val title = document.select("h1").first().text()
        val manga = SManga.create()
        manga.author = "Unknown"
        manga.artist = "Unknown"
        manga.genre = "Unknown"
        manga.description = "Unknown"
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = if (title == "One Shot") "" else setThumbnail(title)
        return manga
    }

    private fun setThumbnail(title: String): String {
        return client.newCall(GET("https://myanimelist.net/manga.php?q=" + title.replace(" ", "%20"), headers)).execute().asJsoup().select("a.hoverinfo_trigger").first().attr("href").let {
            client.newCall(GET(it, headers)).execute().asJsoup().select("img.ac").first().attr("src")
        }
    }

    override fun chapterListSelector() = "table.table-striped > tbody > tr:has(td)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlEl = element.select("td:nth-of-type(1) > a").first()
        val dateEl = element.select("td:nth-of-type(2)")

        val chapter = SChapter.create()
        chapter.url = urlEl.attr("href")
        chapter.name = urlEl.text()
        chapter.date_upload = dateEl.text()?.let { dateParse(it) } ?: 0

        return chapter
    }

    private fun dateParse(dateAsString: String): Long {
        val date: Date = try {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateAsString.replace(Regex("(st|nd|rd|th)"), ""))
        } catch (e: ParseException) {
            val m = datePattern.matcher(dateAsString)

            if (dateAsString != "Today" && m.matches()) {
                val amount = m.group(1).toInt()

                Calendar.getInstance().apply {
                    add(Calendar.DATE, -amount)
                }.time
            } else if (dateAsString == "Today") {
                Calendar.getInstance().time
            } else return 0
        }

        return date.time
    }

    override fun pageListParse(document: Document): List<Page> {
        val num = document.select("div.btn-reader-page > ul.dropdown-menu > li").last().text().substringAfter("Last Page (").substringBefore(")").toInt()
        val url = document.baseUri().substringBeforeLast("1")
        val pages = mutableListOf<Page>()

        for (i in 1..num)
            pages.add(Page(i - 1, url + i))

        pages.getOrNull(0)?.imageUrl = imageUrlParse(document)
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        return "http:" + document.getElementById("manga-page").attr("src")
    }

}
