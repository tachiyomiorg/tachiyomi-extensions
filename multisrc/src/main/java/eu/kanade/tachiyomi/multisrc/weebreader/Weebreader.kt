package eu.kanade.tachiyomi.multisrc.weebreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Weebreader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val mangaUrlDirectory: String = "/titles",
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
) : ParsedHttpSource() {

    override val supportsLatest = false

    private val json: Json by injectLazy()

    private fun Request.appendPathSegment(segment: String) = newBuilder().url(this.url.newBuilder().addPathSegment(segment).build()).build()

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl$mangaUrlDirectory", headers)
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h4").text()
        url = element.selectFirst("a").attr("href")
        thumbnail_url = "$baseUrl${element.selectFirst("img").attr("src")}"
    }

    override fun popularMangaSelector() = ".card > div.content"
    override fun popularMangaNextPageSelector(): String? = null

    // latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    // search
    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not used")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {

        genre = document.select("div.gnr a, .mgen a, .seriestugenre a").joinToString { it.text() }
        status = SManga.UNKNOWN // Is technically present on the manga listing page
        artist = document.selectFirst("p:contains(Artist)")?.text()?.substringAfter("Artist:")?.trim()
        author = document.selectFirst("p:contains(Author)")?.text()?.substringAfter("Author:")?.trim()
        title = document.selectFirst(".content > h1.ui.header").text()
        thumbnail_url = "$baseUrl${document.select("img[alt=Cover]").attr("src")}"
        description = document.select(".content .description > p").joinToString("\n") { it.text() }
        genre = document.select(".ui.labels > .label").joinToString(", ") { it.text() }
    }

    // chapters
    override fun chapterListSelector() = ".item"
    private fun chapterListRequest(page: Int, manga: SManga) = mangaDetailsRequest(manga).appendPathSegment("$page")
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            /**
             * Returns an observable which emits the list of chapters found on a page,
             * for every page starting from specified page
             */
            fun getAllPagesFrom(page: Int, pred: Observable<List<SChapter>> = Observable.just(emptyList())): Observable<List<SChapter>> =
                client.newCall(chapterListRequest(page, manga))
                    .asObservableSuccess()
                    .concatMap { response ->
                        val cp = chapterListParse(response)
                        if (cp.isNotEmpty())
                            getAllPagesFrom(page + 1, pred = pred.concatWith(Observable.just(cp))) // tail call to avoid blowing the stack
                        else // by the pigeon-hole principle
                            pred.concatWith(Observable.just(cp))
                    }
            getAllPagesFrom(1).reduce(List<SChapter>::plus)
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }


    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = "${element.selectFirst("a").attr("href").substringBeforeLast('/')}/json"
        name = element.selectFirst(".content").text()
        date_upload = element.selectFirst(".description").text()?.let { parseChapterDate(it) }
            ?: 0
    }

    // pages

    override fun pageListParse(response: Response): List<Page> {
        return json.decodeFromString<JsonObject>(response.body!!.string())["pages"]!!.jsonArray.map {
            Page(it.jsonObject["number"]!!.jsonPrimitive.int, imageUrl = "$baseUrl${it.jsonObject["address"]!!.jsonPrimitive.content}")
        }
    }
    override fun pageListParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

}
