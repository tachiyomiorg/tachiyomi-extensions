package eu.kanade.tachiyomi.extension.ar.mangaae

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaAe : ParsedHttpSource() {

    override val name = "مانجا العرب"

    override val baseUrl = "https://www.manga.ae"

    override val lang = "ar"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga", headers)
    }

    override fun popularMangaSelector() = "div.mangacontainer"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first().attr("src")
        element.select("a.manga")[1].let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.pagination a.active"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector(): String = "div.popular-manga-container"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val img = element.select("img").first()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = img.attr("alt")
                .split("مانجا ")[1].split(" ch")[0]
        }
        manga.thumbnail_url = img.attr("src")
            return manga
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/manga/search:$query|page:$page"
        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    if(filter.state != 0) {
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
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.indexcontainer").first()
        val manga = SManga.create()
        manga.title = infoElement.select("div.main").first().ownText()
        manga.author = infoElement.select("div.manga-details-author a")[1].ownText()
        val status = infoElement.select("div.manga-details-extended h4")[1].ownText()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("ul > li").mapNotNull{ it.text() }.joinToString(", ")
        manga.description = infoElement.select("div.manga-details-extended h4")[2].ownText()
        manga.thumbnail_url = infoElement.select("img.manga-cover").attr("src")

        return manga
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
            chapter.setUrlWithoutDomain(it.attr("href")
                .replace("/1/", "/0/full"))
            chapter.name = it.text()
        }
        chapter.date_upload = 0

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
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

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
