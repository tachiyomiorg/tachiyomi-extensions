package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getArtists
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getGroups
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getTags
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getTime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

open class NHentai(override val lang: String, private val nhLang: String) : ParsedHttpSource() {

    final override val baseUrl = "https://nhentai.net"
    override val name = "NHentai"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val url = chain.request().url().toString()

        // Artificial delay for images (aka ghetto throttling)
        if (url.contains("i.nh")) {
            Thread.sleep(250)
        }

        chain.proceed(chain.request())
    }.build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi ${System.getProperty("http.agent")}")
    }

    private val searchUrl = "$baseUrl/search"

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = mutableListOf<SChapter>()
        val chapter = SChapter.create().apply {
            name = "Chapter"
            scanlator = getGroups(document)
            date_upload = getTime(document)
            setUrlWithoutDomain(response.request().url().encodedPath())
        }

        chapterList.add(chapter)

        return chapterList
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun getFilterList(): FilterList = FilterList(SortFilter())

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a > div").text().replace("\"", "").trim()
        thumbnail_url = element.select("a > img").attr("data-src").replace("t.nh", "i.nh").replace("t.", ".")
    }

    override fun latestUpdatesNextPageSelector() = "#content > section.pagination > a.next"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/language/$nhLang/?page=$page", headers)

    override fun latestUpdatesSelector() = "#content > div > div"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("#info > h1").text().replace("\"", "").trim()
        //thumbnail_url = document.select("#cover > a > img").attr("data-src")
        status = SManga.COMPLETED
        artist = getArtists(document)
        author = artist
        description = getTags(document)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageElements = document.select("#thumbnail-container > div")
        val pageList = mutableListOf<Page>()

        pageElements.forEach {
            Page(pageList.size).run {
                this.imageUrl = it.select("a > img").attr("data-src").replace("t.nh", "i.nh").replace("t.", ".")

                pageList.add(pageList.size, this)
            }
        }

        return pageList
    }

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl${chapter.url}", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a > div").text().replace("\"", "").trim()
        thumbnail_url = element.select("a > img").attr("data-src").replace("t.nh", "i.nh").replace("t.", ".")
    }

    override fun popularMangaNextPageSelector() = "#content > section.pagination > a.next"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/language/$nhLang/popular?page=$page", headers)

    override fun popularMangaSelector() = "#content > div > div"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a > div").text().replace("\"", "").trim()
        thumbnail_url = element.select("a > img").attr("data-src").replace("t.nh", "i.nh").replace("t.", ".")
    }

    override fun searchMangaNextPageSelector() = "#content > section.pagination > a.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val stringBuilder = StringBuilder()
        stringBuilder.append(searchUrl)
        stringBuilder.append("/?q=${URLEncoder.encode("$query +$nhLang", "UTF-8")}&")

        filters.forEach {
            when (it) {
                is SortFilter -> stringBuilder.append("sort=${it.values[it.state].toLowerCase()}&")
            }
        }

        stringBuilder.append("page=$page")

        return GET(stringBuilder.toString(), headers)
    }

    override fun searchMangaSelector() = "#content > div > div"
}
