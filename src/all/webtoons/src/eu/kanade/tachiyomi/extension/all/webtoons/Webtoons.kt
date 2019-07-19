package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

abstract class Webtoons(override val lang: String) : ParsedHttpSource() {

    override val name = "Webtoons.com"

    override val baseUrl = "http://www.webtoons.com"

    override val supportsLatest = true

    val day: String
        get() {
            return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "div._list_SUNDAY"
                Calendar.MONDAY -> "div._list_MONDAY"
                Calendar.TUESDAY -> "div._list_TUESDAY"
                Calendar.WEDNESDAY -> "div._list_WEDNESDAY"
                Calendar.THURSDAY -> "div._list_THURSDAY"
                Calendar.FRIDAY -> "div._list_FRIDAY"
                Calendar.SATURDAY -> "div._list_SATURDAY"
                else -> {
                    "div"
                }
            }
        }

    override fun popularMangaSelector() = "div.left_area > ul.lst_type1 > li"

    override fun latestUpdatesSelector() = "div#dailyList > $day li > a:has(span:contains(UP))"

    override fun headersBuilder() = super.headersBuilder()
            .add("Referer", "http://www.webtoons.com/en/")

    protected val mobileHeaders = super.headersBuilder()
            .add("Referer", "http://m.webtoons.com")
            .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/en/top", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)

    private fun mangaFromElement(query: String, element: Element): SManga {
        val manga = SManga.create()
        element.select(query).first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("p.subj").text()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement("a", element)

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
        val url = HttpUrl.parse("$baseUrl/search?keyword=$query")?.newBuilder()!!
        url.addQueryParameter("searchType", "WEBTOON")
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request().url().queryParameter("keyword")
        val toonDocument = response.asJsoup()
        val discDocument = client.newCall(GET("$baseUrl/search?keyword=$query&searchType=CHALLENGE", headers)).execute().asJsoup()

        val elements = mutableListOf<Element>().apply {
            addAll(toonDocument.select(searchMangaSelector()))
            addAll(discDocument.select(searchMangaSelector()))
        }

        val mangas = elements.map { element ->
            searchMangaFromElement(element)
        }

        return MangasPage(mangas, false)
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

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("#content > div.cont_box > div.detail_header > div.info")
        val infoElement = document.select("#_asideDetail")
        val picElement = document.select("#content > div.cont_box > div.detail_body")
        val discoverPic = document.select("#content > div.cont_box > div.detail_header > span.thmb")

        val manga = SManga.create()
        manga.author = detailElement.select(".author:nth-of-type(1)").text().substringBefore("author info")
        manga.artist = detailElement.select(".author:nth-of-type(2)").first()?.text()?.substringBefore("author info") ?: manga.author
        manga.genre = detailElement.select(".genre").map { it.text() }.joinToString(", ")
        manga.description = infoElement.select("p.summary").text()
        manga.status = infoElement.select("p.day_info").text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = discoverPic.select("img").not("[alt='Representative image']").first()?.attr("src") ?: picElement.attr("style")?.substringAfter("url(")?.substringBeforeLast(")")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("UP") -> SManga.ONGOING
        status.contains("COMPLETED") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun imageUrlParse(document: Document) = document.select("img").first().attr("src")

}
