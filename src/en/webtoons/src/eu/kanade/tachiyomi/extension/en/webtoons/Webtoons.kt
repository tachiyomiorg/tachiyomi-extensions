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

class Webtoons : ParsedHttpSource() {

    override val name = "Webtoons.com"

    override val baseUrl = "http://www.webtoons.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = ".challenge_lst > ul > li"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun headersBuilder() = super.headersBuilder()
            .add("Referer", "http://www.webtoons.com/en/")

    private val mobileHeaders = super.headersBuilder()
            .add("Referer", "http://m.webtoons.com")
            .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/en/challenge/list?genreTab=ALL&sortOrder=READ_COUNT", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/challenge/list?genreTab=ALL&sortOrder=UPDATE", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.let {
            manga.setUrlWithoutDomain(it.select("a").attr("href"))
            manga.title = it.select("p.subj").text()
            manga.author = it.select(".author").text()
            manga.thumbnail_url = it.select("img").attr("src").substringBefore('?')
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".paginate a[href='#'] + a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search?keyword=$query&searchType=WEBTOON")?.newBuilder()!!
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

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select(".detail_header > .info")
        val infoElement = document.select("#_asideDetail")
        val picElement = document.select("#content > div.cont_box > div.detail_body")
        val discoverPic = document.select("#content > div.cont_box > div.detail_header > span.thmb")

        val manga = SManga.create()
        manga.author = detailElement.select(".author:nth-of-type(1)").text().substringBefore("author info")
        manga.artist = detailElement.select(".author:nth-of-type(2)").first()?.text()?.substringBefore("author info") ?: manga.author
        manga.genre = detailElement.select(".genre").text()
        manga.description = infoElement.select("p.summary").text()
        manga.status = infoElement.select("p.day_info").text().orEmpty().let { parseStatus(it) }
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
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("a > div.row > div.info > p.sub_title > span.ellipsis").text()
        val select = element.select("a > div.row > div.num")
        if (select.isNotEmpty()) {
            chapter.name = chapter.name + " Ch. " + select.text().substringAfter("#")
        }
        chapter.date_upload = element.select("a > div.row > div.info > p.date").text()?.let { SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(it).time } ?: 0
        return chapter
    }

    override fun chapterListRequest(manga: SManga) = GET("http://m.webtoons.com" + manga.url, mobileHeaders)

    override fun pageListParse(document: Document) = document.select("div#_imageList > img").mapIndexed { i, element -> Page(i, "", element.attr("data-url")) }

    override fun imageUrlParse(document: Document) = document.select("img").first().attr("src")

}
