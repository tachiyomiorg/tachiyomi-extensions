package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.util.Calendar
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class Webtoons(
    override val lang: String,
    open val langCode: String = lang,
    open val localeForCookie: String = lang
) : ParsedHttpSource() {

    override val name = "Webtoons.com"

    override val baseUrl = "https://www.webtoons.com"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return listOf<Cookie>(
                    Cookie.Builder()
                        .domain("www.webtoons.com")
                        .path("/")
                        .name("ageGatePass")
                        .value("true")
                        .name("locale")
                        .value(localeForCookie)
                        .build()
                )
            }
        })
        .build()

    private val day: String
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

    override fun popularMangaSelector() = "not using"

    override fun latestUpdatesSelector() = "div#dailyList > $day li > a:has(span:contains(UP))"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
            .add("Referer", "https://www.webtoons.com/$langCode/")

    protected val mobileHeaders: Headers = super.headersBuilder()
            .add("Referer", "https://m.webtoons.com")
            .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$langCode/dailySchedule", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()
        var maxChild = 0

        // For ongoing webtoons rows are ordered by descending popularity, count how many rows there are
        document.select("div#dailyList > div").forEach { day ->
            day.select("li").count().let { rowCount ->
                if (rowCount > maxChild) maxChild = rowCount
            }
        }

        // Process each row
        for (i in 1..maxChild) {
            document.select("div#dailyList > div li:nth-child($i) a").map { mangas.add(popularMangaFromElement(it)) }
        }

        // Add completed webtoons, no sorting needed
        document.select("div.daily_lst.comp li a").map { mangas.add(popularMangaFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$langCode/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("p.subj").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector(): String? = null

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

    override fun searchMangaSelector() = "#content > div.card_wrap.search li a"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    open fun parseDetailsThumbnail(document: Document): String? {
        val picElement = document.select("#content > div.cont_box > div.detail_body")
        val discoverPic = document.select("#content > div.cont_box > div.detail_header > span.thmb")
        return discoverPic.select("img").not("[alt='Representative image']").first()?.attr("src") ?: picElement.attr("style")?.substringAfter("url(")?.substringBeforeLast(")")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("#content > div.cont_box > div.detail_header > div.info")
        val infoElement = document.select("#_asideDetail")

        val manga = SManga.create()
        manga.author = detailElement.select(".author:nth-of-type(1)").first()?.ownText()
        manga.artist = detailElement.select(".author:nth-of-type(2)").first()?.ownText() ?: manga.author
        manga.genre = detailElement.select(".genre").joinToString(", ") { it.text() }
        manga.description = infoElement.select("p.summary").text()
        manga.status = infoElement.select("p.day_info").text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = parseDetailsThumbnail(document)
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("UP") -> SManga.ONGOING
        status.contains("COMPLETED") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun imageUrlParse(document: Document): String = document.select("img").first().attr("src")
}
