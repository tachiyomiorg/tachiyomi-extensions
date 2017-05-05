package eu.kanade.tachiyomi.extension.en.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

/**
 *  Todo Cover -> crop right //possible?
 */
class Webtoons : ParsedHttpSource() {

    override val id: Long = 22

    override val name = "Webtoons.com"

    override val baseUrl = "http://www.webtoons.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = "div.left_area > ul.lst_type1 > li"

    override fun latestUpdatesSelector() : String {
        val day = getDay()
        return "div#dailyList > $day li > a:contains(UP)"
    }

    override fun headersBuilder() = super.headersBuilder()
            .add("Referer", "http://www.webtoons.com/en/")

    private val mobileHeaders = super.headersBuilder()
            .add("Referer", "http://m.webtoons.com")
            .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/en/top", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/en/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)
    }

    private fun mangaFromElement(query: String, element: Element): SManga {
        val manga = SManga.create()
        element.select(query).first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("p.subj").text()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return mangaFromElement("a", element)
    }

    fun getDay() : String {
        val cal : Calendar = Calendar.getInstance()
        var selector : String = ""
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> selector = "div._list_SUNDAY"
            Calendar.MONDAY -> selector = "div._list_MONDAY"
            Calendar.TUESDAY -> selector = "div._list_TUESDAY"
            Calendar.WEDNESDAY -> selector = "div._list_WEDNESDAY"
            Calendar.THURSDAY -> selector = "div._list_THURSDAY"
            Calendar.FRIDAY -> selector = "div._list_FRIDAY"
            Calendar.SATURDAY -> selector = "div._list_SATURDAY"
            Calendar.SUNDAY -> selector = "div._list_SUNDAY"
        }
        return selector
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("p.subj").text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search?keyword=$query").newBuilder()

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Type -> url.addQueryParameter("searchType", arrayOf("WEBTOON", "CHALLENGE")[filter.state])
            }
        }

        url.addQueryParameter("page", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "#content > div.card_wrap.search li"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("p.subj").text()
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "div.paginate > a[href=#]"

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector)?.first()?.nextElementSibling()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("#content > div.cont_box > div.detail_header > div.info").first()
        val infoElement = document.select("#_asideDetail").first()
        val picElement = document.select("#content > div.cont_box > div.detail_body").first()
        val discoverPic = document.select("#content > div.cont_box > div.detail_header > span.thmb").first()

        val manga = SManga.create()
        manga.author = detailElement.select(".author:nth-of-type(1)").first()?.text()?.substringBefore("author info")
        manga.artist = detailElement.select(".author:nth-of-type(2)").first()?.text()?.substringBefore("author info") ?: manga.author
        manga.genre = detailElement.select(".genre").first()?.text()
        manga.description = infoElement.select("p.summary").first()?.text()
        manga.status = infoElement.select("p.day_info").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = discoverPic.select("img").not("[alt=Representative image").first()?.attr("src") ?: picElement.attr("style")?.substringAfter("url(")?.substringBeforeLast(")")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("UP") -> SManga.ONGOING
        status.contains("COMPLETED") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul#_episodeList > li[id*=episode]"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("a > div.row > div.info > p.sub_title > span.ellipsis").first()?.text() + ""
        chapter.date_upload = element.select("a > div.row > div.info > p.date").first()?.text()?.let { SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(it).time } ?: 0
        return chapter
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("http://m.webtoons.com" + manga.url, mobileHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        for ((i, element) in document.select("div#_imageList > img").withIndex()) {
            pages.add(Page(i, "", element.attr("data-url")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("img").first().attr("src")
    }

    private class Type : Filter.Select<String>("Type", arrayOf("Webtoon (default)", "Discover"))

    override fun getFilterList() = FilterList(
            Type()
    )
}