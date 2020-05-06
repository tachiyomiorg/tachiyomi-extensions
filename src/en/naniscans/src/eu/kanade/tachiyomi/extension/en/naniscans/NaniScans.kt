package eu.kanade.tachiyomi.extension.en.naniscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class NaniScans : ParsedHttpSource() {

    override val name = "NANI? Scans"

    override val baseUrl = "https://naniscans.xyz"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/titles", headers)
    }

    override fun popularMangaSelector() = "div.card"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h4 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(super.latestUpdatesParse(response).mangas.distinctBy { it.title }, false)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    // website doesn't have a search function
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(1))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val mangas = popularMangaParse(response).mangas.filter { it.title.contains(query, ignoreCase = true) }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div.content").let { info ->
                author = info.select("div.center p:contains(Author:)").text().substringAfter("Author: ")
                artist = info.select("div.center p:contains(Artist:)").text().substringAfter("Artist: ")
                genre = info.select("div.labels > div").joinToString { it.text() }
                description = info.select("div.description p").text()
            }
            thumbnail_url = document.select("div.image img").attr("abs:src")
        }
    }

    // Chapters

    override fun chapterListSelector() = "div.list div.item"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("p.header a:last-of-type").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("div.description p").firstOrNull()?.ownText()
                ?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it).time } ?: 0
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(let pages)").first().data().let { script ->
            script.substringAfter("let pages = [").substringBefore("]").replace("\"", "")
                .split(",").mapIndexed { i, string -> Page(i, "", baseUrl + string) }
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
