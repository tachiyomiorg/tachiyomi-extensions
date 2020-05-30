package eu.kanade.tachiyomi.extension.id.kombatch

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject

class Kombatch: ParsedHttpSource() {
    override val name = "Kombatch"

    override val baseUrl = "https://kombatch.com"

    override val lang = "id"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)

    override fun popularMangaSelector() = "div.box_trending"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a._2dU-m.vlQGQ").attr("href"))
        title = element.select("a._2dU-m.vlQGQ").text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "[rel=next]"

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.row.no-gutters:has(h3.text-truncate):has(a:has(img))"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a:has(img)").attr("href"))
        title = element.select("a.text-black-50").first().text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    //No next page
    override fun latestUpdatesNextPageSelector() = "Not used"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search?search=$query&page=$page", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val details = document.select("div.spe > span")
        description = document.select("[itemprop=articleBody]").text().trim()
        status = parseStatus(details.first().text())
        author = details[1].ownText().trim()
        genre = details[2].select("a").joinToString { it.text() }
        artist = details[3].ownText().trim()
        thumbnail_url = document.select("div.thumb > img").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("ONGOING") -> SManga.ONGOING
        status.contains("COMPLETE") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".lchx.mobile > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href").replace("/read/", "/api/chapter/"))
        name = element.text().trim()
    }

    override fun pageListParse(document: Document): List<Page> {
        val response = JSONObject(document.select("body").text())
        val pages = response.getJSONObject("chapter").getJSONArray("images")
        val finalPages = mutableListOf<Page>()

        for (i in 0 until pages.length()) {
            finalPages.add(Page(i, "", pages.getJSONObject(i)["text"].toString().replace("//", "https://")))
        }

        return finalPages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
