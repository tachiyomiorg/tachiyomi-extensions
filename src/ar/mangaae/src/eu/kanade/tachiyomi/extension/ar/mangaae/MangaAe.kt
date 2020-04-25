package eu.kanade.tachiyomi.extension.ar.mangaae

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaAe : ParsedHttpSource() {

    override val name = "مانجا العرب"

    override val baseUrl = "https://www.mangaae.com"

    override val lang = "ar"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/73.0")
        .add("Referer", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/page:$page", headers)
    }

    override fun popularMangaNextPageSelector() = "div.pagination a:last-child:not(.active)"

    override fun popularMangaSelector() = "div.mangacontainer"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val lazysrc = element.select("img").attr("data-pagespeed-lazy-src")
        thumbnail_url = if (lazysrc.isNullOrEmpty()) {
            element.select("img").attr("src")
        } else {
            lazysrc
        }
        element.select("div.mangacontainer a.manga")[0].let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector(): String = "div.popular-manga-container"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val lazysrc = element.select("img").attr("data-pagespeed-lazy-src")
        thumbnail_url = if (lazysrc.isNullOrEmpty()) {
            element.select("img").attr("src")
        } else {
            lazysrc
        }
        element.select("a")[2].let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/manga/search:$query|page:$page"
        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    if (filter.state != 0) {
                        url += "|order:${filter.toUriPart()}"
                    }
                }
            }
        }
        url += "|arrange:minus"
        return GET(HttpUrl.parse(url)!!.newBuilder().build().toString(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga summary page
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.indexcontainer").first()
        title = infoElement.select("h1.EnglishName").text().removeSurrounding("(", ")")
        author = infoElement.select("div.manga-details-author h4")[0].text()
        artist = author
        status = parseStatus(infoElement.select("div.manga-details-extended h4")[1].text())
        genre = infoElement.select("div.manga-details-extended a[href*=tag]").map { it.text() }.joinToString(", ")
        description = infoElement.select("div.manga-details-extended h4")[2].text()
        thumbnail_url = infoElement.select("img.manga-cover").attr("src")
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("مستمرة") -> SManga.ONGOING
        status.contains("مكتملة") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListSelector() = "ul.new-manga-chapters > li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("a").let {
            // use full pages for easier links
            chapter.setUrlWithoutDomain(it.attr("href").removeSuffix("/1/") + "/0/full")
            chapter.name = "\u061C" + it.text() // Add unicode ARABIC LETTER MARK to ensure all titles are right to left
        }
        return chapter
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div#showchaptercontainer img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not used")

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class OrderByFilter : UriPartFilter("الترتيب حسب", arrayOf(
        Pair("اختيار", ""),
        Pair("اسم المانجا", "english_name"),
        Pair("تاريخ النشر", "release_date"),
        Pair("عدد الفصول", "chapter_count"),
        Pair("الحالة", "status")
    ))

    override fun getFilterList() = FilterList(
        OrderByFilter()
    )
}
