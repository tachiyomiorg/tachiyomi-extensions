package eu.kanade.tachiyomi.extension.en.loadla

/**
 *
 * The Search part is mostly taken from the NHentai Extension
 */

import android.util.Log
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class LoadLa: ParsedHttpSource() {

    override val name = "Load.la"

    override val baseUrl = "https://box.load.la"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    override fun chapterListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(listOf(SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(manga.url)
        }))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        log(response.request().url().encodedPath())
        return listOf(SChapter.create().apply {
        })
    }

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi/${BuildConfig.VERSION_NAME} ${System.getProperty("http.agent")}")
        add("Host", "box.load.la")
        add("cookie", "__cfduid=d480d1fbc4be893d08c30851a5ab607c41587031301; xo=1")
        add("referer", "https://www.load.la/?o=1")
//        add("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.92 Safari/537.36")
        add("accept", "*/*")
    }


    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request = debugRequest(GET("$baseUrl/?re=&Series=&q=&num=${page * 33}&shownew=&pages=&color=&tag=&related=&art=&b=", headers))

    override fun mangaDetailsRequest(manga: SManga): Request = debugRequest(GET("$baseUrl${manga.url}", headers))

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        genre = document.select("div.pagination > .pagination_number").filter {
            it.text() != "Tags"
        }.map { it.text() }.sorted().joinToString(", ")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = chapter.url.replace("&o=", "&slideshow=play&o=")
        lastList = url
        return debugRequest(GET("$baseUrl${url}", headersBuilder().add("referer", baseUrl+chapter.url).build()))
    }

    override fun pageListParse(document: Document): List<Page> {
        val links = document.select(".slideshow_path").map { it.text() }
        val pageList = mutableListOf<Page>()
        links.forEach {
            Page(pageList.size).run {
                this.imageUrl = it
                pageList.add(pageList.size, this)
            }
        }
        return pageList
    }

    var lastList = ""

    override fun imageRequest(page: Page): Request {
        val lHeaders = headersBuilder().add("referer", baseUrl + lastList).build()
        return debugRequest(GET(page.imageUrl.toString(), lHeaders))
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("a:has(img)").attr("href"))
            title = element.select("a .pagination").text()
            thumbnail_url = element.select(" a img").attr("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = ".pagination .pagination_number"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?re=&Series=&q=&num=${page * 33}&shownew=&pages=&color=&tag=&related=&art=&b=365d", headers)
//    override fun popularMangaRequest(page: Int): Request {
//        val request = GET("$baseUrl/?o=1", headers)
//       return debugRequest(request)
//    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        client.newCall(popularMangaRequest((page))).asObservableSuccess().map { response ->
            debugResponse(response)
        }
        return super.fetchPopularManga(page)
    }

    override fun popularMangaSelector(): String = "td.search_gallery_item[style]:not([style=\"width:214px;border-color:#FF0000\"])"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filters = if (filters.isEmpty()) getFilterList() else filters
        val url = HttpUrl.parse("$baseUrl/")!!.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("num", "${page * 33}")

        filters.findInstance<SortFilter>()?.let { f ->
            if (f.state == 1) url.addQueryParameter("shownew", "favs")
            if (f.state == 2) url.addQueryParameter("shownew", "rating")
        }

        filters.findInstance<SizeFilter>()?.let { f ->
            if (f.state == 1) url.addQueryParameter("pages", "Short")
            if (f.state == 2) url.addQueryParameter("pages", "Medium")
            if (f.state == 3) url.addQueryParameter("pages", "Large")
            if (f.state == 4) url.addQueryParameter("pages", "Mega")
        }

        filters.findInstance<ColorFilter>()?.let { f ->
            if (f.state) url.addQueryParameter("color", "Color")
        }

        return debugRequest(GET(url.toString(), headers))
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        ColorFilter(),
        SizeFilter()
    )

    fun log(content: String) {
        Log.i("[Load.la]", content)
    }

    fun debugRequest(request: Request): Request {
        log("url: ${request.url()}")
        log("headers: ${request.headers()}")
        return request
    }

    fun debugResponse(request: Response): Response {
        log("headers: ${request.headers()}")
        log("Body: ${request.body()}")
        return request
    }

    private class SortFilter : Filter.Select<String>("Sort", arrayOf("Popular", "Latest", "Rating"))
    private class ColorFilter : Filter.CheckBox("Color only")
    private class SizeFilter : Filter.Select<String>("Hentai size", arrayOf("All", "Short (1-15 pages)", "Medium (16-40)", "Large (41-100)", "Mega (100+)"))

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
