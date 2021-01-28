package eu.kanade.tachiyomi.extension.fr.scantrad

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.random.Random

class Scantrad : ParsedHttpSource() {

    override val name = "Scantrad"

    override val baseUrl = "https://scantrad.net"

    override val lang = "fr"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(1)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    protected open val userAgentRandomizer1 = "${Random.nextInt(9).absoluteValue}"
    protected open val userAgentRandomizer2 = "${Random.nextInt(10,99).absoluteValue}"
    protected open val userAgentRandomizer3 = "${Random.nextInt(100,999).absoluteValue}"

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", baseUrl)
        add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/8$userAgentRandomizer1.0.4$userAgentRandomizer3.1$userAgentRandomizer2 Safari/537.36"
        )
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas", headers)
    }

    override fun popularMangaSelector() = "div.h-left a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.hmi-titre").text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()

        document.select(latestUpdatesSelector()).map { mangas.add(latestUpdatesFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesSelector() = "div.h-left > div.home-manga"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("div.hmi-titre a").first().attr("abs:href"))
        manga.title = element.select("div.hmi-titre a").first().text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "not needed"

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        return MangasPage(popularMangaParse(response).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        document.select("div.mf-info").let {
            manga.title = it.select("div.titre").text()
            manga.description = it.select("div.synopsis").text()
            manga.thumbnail_url = it.select("div.poster img").attr("abs:src")
            manga.status = parseStatus(it.select("div.sub-i:last-of-type span").text())
        }

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    // ignore links from Amazon that get mixed in
    override fun chapterListSelector() = "div.chapitre:has(span:contains(lire))"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.select("a.chr-button").attr("href"))
        chapter.name = element.select("div.chl-titre").text()
        chapter.date_upload = parseChapterDate(element.select("div.chl-date").text())

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(" ")[3].toIntOrNull()

        return if (value != null) {
            when (date.split(" ")[4]) {
                "minute", "minutes" -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "heure", "heures" -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "jour", "jours" -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "semaine", "semaines" -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "mois" -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                "an", "ans", "année" -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                else -> {
                    return 0L
                }
            }
        } else {
            try {
                SimpleDateFormat("dd MMM yyyy", Locale.FRENCH).parse(date.substringAfter("le "))?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.sc-lel img[id]").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:data-src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
