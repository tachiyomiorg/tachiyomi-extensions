package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getArtists
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getGroups
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getTags
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getDesc
import eu.kanade.tachiyomi.extension.all.nhentai.NHUtils.Companion.getTime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class NHentai(override val lang: String, private val nhLang: String) : ParsedHttpSource() {

    final override val baseUrl = "https://nhentai.net"

    override val name = "NHentai"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi/${BuildConfig.VERSION_NAME} ${System.getProperty("http.agent")}")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/language/$nhLang/?page=$page", headers)

    override fun latestUpdatesSelector() = "#content .gallery"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a > div").text().replace("\"", "").trim()
        thumbnail_url = element.select(".cover img").attr("data-src")
    }

    override fun latestUpdatesNextPageSelector() = "#content > section.pagination > a.next"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/language/$nhLang/popular?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
                .addQueryParameter("q", "$query +$nhLang")
                .addQueryParameter("page", page.toString())

        filters.forEach {
            when (it) {
                is SortFilter -> url.addQueryParameter("sort", it.values[it.state].toLowerCase())
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("#info > h1").text().replace("\"", "").trim()
        thumbnail_url = document.select("#cover > a > img").attr("data-src")
        status = SManga.COMPLETED
        artist = getArtists(document)
        author = artist
        description = getDesc(document)
        genre = getTags(document)
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(SChapter.create().apply {
            name = "Chapter"
            scanlator = getGroups(document)
            date_upload = getTime(document)
            setUrlWithoutDomain(response.request().url().encodedPath())
        })
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl${chapter.url}", headers)

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

    override fun getFilterList(): FilterList = FilterList(SortFilter())

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

}
