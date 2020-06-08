package eu.kanade.tachiyomi.extension.ja.mangaraw

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Protocol
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class MangaRaw(
    override val name: String,
    override val baseUrl: String
) : ParsedHttpSource() {

    override val lang = "ja"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/seachlist/page/$page/?cat=-1", headers)

    override fun popularMangaSelector() = "article"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a:has(img)").attr("href"))
        title = element.select("img").attr("alt").substringBefore("(RAW – Free)").trim()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = ".next.page-numbers"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/newmanga/page/$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        genre = document.select("p.has-text-color:has(strong) a").joinToString { it.text() }
        description = document.select("p.has-text-color:not(:has(strong))").first().text()
        thumbnail_url = document.select(".wp-block-image img").attr("abs:src")
    }

    override fun chapterListSelector() = ".chapList a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text().trim()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".wp-block-image > img").mapIndexed { i, element ->
            val attribute = if (element.hasAttr("data-src")) "data-src" else "src"
            Page(i, "", element.attr(attribute))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
