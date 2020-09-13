package eu.kanade.tachiyomi.extension.en.mangakatana

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKatana : ParsedHttpSource() {
    override val name = "MangaKatana"

    override val baseUrl = "https://mangakatana.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addNetworkInterceptor { chain ->
        val originalResponse = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type").contains("application/octet-stream")) {
            val orgBody = originalResponse.body()!!.bytes()
            val extension = chain.request().url().toString().substringAfterLast(".")
            val newBody = ResponseBody.create(MediaType.parse("image/$extension"), orgBody)
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }.build()

    override fun latestUpdatesSelector() = "div#book_list > div.item"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.text > h3 > a").attr("href"))
        title = element.select("div.text > h3 > a").text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = ".next.page-numbers"

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page?search=$query&search_by=book_name", headers)

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".author").text()
        description = document.select(".summary > p").text()
        status = parseStatus(document.select(".value.status").text())
        genre = document.select(".genres > a").joinToString { it.text() }
        thumbnail_url = document.select("div.media div.cover img").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "tr:has(.chapter)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text()
        date_upload = dateFormat.parse(element.select(".update_time").text())?.time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM-dd-yyyy", Locale.US)
        }
    }

    private val imageArrayRegex = Regex("""var ytaw=\[([^\[]*)]""")
    private val imageUrlRegex = Regex("""'([^']*)'""")

    override fun pageListParse(document: Document): List<Page> {
        val imageArray = document.select("script:containsData(var ytaw)").firstOrNull()?.data()
            ?.let { imageArrayRegex.find(it)?.groupValues?.get(1) }
            ?: throw Exception("Image array not found")
        return imageUrlRegex.findAll(imageArray).asIterable().mapIndexed { i, mr ->
            Page(i, "", mr.groupValues[1])
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
}
