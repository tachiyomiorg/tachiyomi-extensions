package eu.kanade.tachiyomi.extension.en.loadla

/**
 *
 * The Search part is mostly taken from the NHentai Extension
 */

import eu.kanade.tachiyomi.extensions.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class LoadLa: ParsedHttpSource() {

    override val name = "Load.la"

    override val baseUrl = "https://box.load.la"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    override fun chapterListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(response.request().url().encodedPath())
        })
    }

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi/${BuildConfig.VERSION_NAME} ${System.getProperty("http.agent")}")
        add("referer", "https://www.load.la/?o=1")
        add("cookie", "cfduid=dc0aceff316236f74e38a99d76fa3802d1586980913; xo=1")
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?re=&Series=&q=&num=${page * 33}&shownew=&pages=&color=&tag=&related=&art=&b=", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        genre = document.select("div.pagination > .pagination_number").filter {
            it.text() != "Tags"
        }.map { it.text() }.sorted().joinToString(", ")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = chapter.url.replace("&o=", "&slideshow=play&o=")
        lastList = url
        return GET("$baseUrl${url}", headers)
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
        return GET(page.imageUrl.toString(), lHeaders)
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

        return GET(url.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        ColorFilter(),
        SizeFilter()
    )

    private class SortFilter : Filter.Select<String>("Sort", arrayOf("Popular", "Latest", "Rating"))
    private class ColorFilter : Filter.CheckBox("Color only")
    private class SizeFilter : Filter.Select<String>("Hentai size", arrayOf("All", "Short (1-15 pages)", "Medium (16-40)", "Large (41-100)", "Mega (100+)"))

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
