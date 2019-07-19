package eu.kanade.tachiyomi.extension.en.comicextra

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class ComicExtra : ParsedHttpSource() {

    override val name = "ComicExtra"

    override val baseUrl = "https://www.comicextra.com"

    override val lang = "en"

    override val supportsLatest = true

    private val datePattern = Pattern.compile("(\\d+) days? ago")

    override fun popularMangaSelector() = "div.cartoon-box"

    override fun latestUpdatesSelector() = "div.hl-box"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/popular-comic", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic-updates", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/comic-search?key=$query", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.mb-right > h3 > a").attr("href"))
        title = element.select("div.mb-right > h3 > a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.hlb-t > a").attr("href"))
        title = element.select("div.hlb-t > a").text()
        thumbnail_url = fetchThumbnailURL(element.select("div.hlb-t > a").attr("href"))
    }

    private fun fetchThumbnailURL(url: String) = client.newCall(GET(url, headers)).execute().asJsoup().select("div.movie-l-img > img").attr("src")

    private fun fetchChaptersFromNav(url: String) = client.newCall(GET(url, headers)).execute().asJsoup().select(chapterListSelector())

    override fun popularMangaNextPageSelector() = "div.general-nav > a:contains(Next)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("span.title-1").text()
        manga.thumbnail_url = document.select("div.movie-l-img > img").attr("src")

        val status = document.select("dt:contains(Status:) + dd").text()
        manga.status = parseStatus(status)
        manga.author = document.select("dt:contains(Author:) + dd").text()
        manga.description = document.select("div#film-content").text()

        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {

        val document = response.asJsoup()
        val nav = document.getElementsByClass("general-nav").first()
        val chapters = ArrayList<SChapter>()

        document.select(chapterListSelector()).forEach {
            chapters.add(chapterFromElement(it))
        }

        if (nav == null) {
            return chapters
        }

        val links = nav.getElementsByTag("a")

        links.forEach {
            if (it.text() != "Next") {
                fetchChaptersFromNav(it.attr("href")).forEach { page ->
                    chapters.add(chapterFromElement(page))
                }
            }
        }

        return chapters
    }

    override fun chapterListSelector() = "table.table > tbody#list > tr:has(td)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlEl = element.select("td:nth-of-type(1) > a").first()
        val dateEl = element.select("td:nth-of-type(2)")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlEl.attr("href"))
        chapter.name = urlEl.text()
        chapter.date_upload = dateEl.text()?.let { dateParse(it) } ?: 0
        return chapter
    }

    private fun dateParse(dateAsString: String): Long {
        var date: Date
        try {
            date = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateAsString.replace(Regex("(st|nd|rd|th)"), ""))
        } catch (e: ParseException) {
            val m = datePattern.matcher(dateAsString)

            if (dateAsString != "Today" && m.matches()) {
                val amount = m.group(1).toInt()

                date = Calendar.getInstance().apply {
                    add(Calendar.DATE, -amount)
                }.time
            } else if (dateAsString == "Today") {
                date = Calendar.getInstance().time
            } else return 0
        }

        return date.time
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val urls = document.select("select.full-select[name=page_select] > option")

        for (element in urls) {
            val url = element.attr("value")
            if (url.isEmpty()) continue
            pages.add(Page(index = Integer.valueOf(element.text()) - 1, url = url))
        }
        return pages
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun fetchImageUrl(page: Page) = client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { realImageUrlParse(it) }!!

    private fun realImageUrlParse(response: Response) = with(response.asJsoup()) {
        getElementById("main_img").attr("src")
    }!!

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Unused method was called somehow!")
}

