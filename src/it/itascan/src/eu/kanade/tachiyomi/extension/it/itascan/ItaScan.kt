package eu.kanade.tachiyomi.extension.it.itascan

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ItaScan : ParsedHttpSource() {

    override val name = "ItaScan"

    override val baseUrl = "https://itascan.info"

    override val lang = "it"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/directory/?&page=$page", headers)
    }

    override fun popularMangaSelector() = "div.col-xs-12 div.col-xs-6"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h4 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "ul.pagination a.next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesSelector() = "table.table-last tbody tr:has(a)"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a.index_tooltip").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/directory/?searchManga=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div[role=main] > div.row > div.col-xs-12").let { info ->
                title = info.select("h3").text()
                author = info.select("strong:contains(Autore:) + a").text()
                thumbnail_url = info.select("img[alt]").attr("abs:src")
                description = info.select("div.col-xs-12.col-md-12.hspace10").text()
                info.select("div.col-md-8").first().let {
                    artist = it.select("strong:contains(Disegnatore:)").firstOrNull()?.nextSibling()?.toString()
                    genre = it.select("strong:contains(Genere:)").firstOrNull()?.nextSibling()?.toString()
                    status = it.select("strong:contains(Stato pubblicazione:)").firstOrNull()?.nextSibling()?.toString().toStatus()
                }
            }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Concluso", ignoreCase = true) -> SManga.COMPLETED
        this.contains("In Corso", ignoreCase = true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "table.table-last tbody tr:has(a)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("td:first-child a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            scanlator = element.select("td:nth-child(3)").text()
        }
    }

    // Pages

    private val gson by lazy { Gson() }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(site_url)").first().data()
            .substringAfter("pages =").substringBefore("title").trim().removeSuffix(",").let { jsonObject ->
                gson.fromJson<JsonObject>(jsonObject)["source"].asJsonArray.mapIndexed { i, json ->
                    Page(i, "", "https:" + json["image"].asString.replace("\\", ""))
                }
            }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
