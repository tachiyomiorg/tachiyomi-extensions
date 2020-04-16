package eu.kanade.tachiyomi.extension.ar.mangalink

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaLink : ParsedHttpSource() {

    override val name = "MangaLink"

    override val baseUrl = "https://mangalink.cc"

    override val lang = "ar"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas?page=$page")
    }

    override fun popularMangaSelector() = "div.card"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a:has(h6)").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:data-src")
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/mangas?page=$page&query=$query", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div.card").first().let { info ->
                title = info.select("h1").text()
                genre = info.select("span.d-flex a.btn").joinToString { it.text() }
                description = info.select("p.card-text").text()
                thumbnail_url = info.select("img").attr("abs:src")
            }
        }
    }

    // Chapters

    override fun chapterListSelector() = "div.card-body > a.btn"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = "# ${element.text()}"
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#content img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
