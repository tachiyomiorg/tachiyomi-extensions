package eu.kanade.tachiyomi.extension.fr.mangakawaii

import android.net.Uri
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaKawaii : ParsedHttpSource() {

    override val name = "Mangakawaii"
    override val baseUrl = "https://www.mangakawaii.com"
    override val lang = "fr"
    override val supportsLatest = true
    private val rateLimitInterceptor = RateLimitInterceptor(1) // 1 request per second

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0")
    }

    override fun popularMangaSelector() = "a.hot-manga__item"
    override fun latestUpdatesSelector() = ".section__list-group li div.section__list-group-left"
    override fun searchMangaSelector() = "h1 + ul a[href*=manga]"
    override fun chapterListSelector() = "tr[class*=volume-]:has(td)"

    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun searchMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("search_type", "manga")
        return GET(uri.toString(), headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("href")
        manga.title = element.select("div.hot-manga__item-caption").select("div.hot-manga__item-name").text().trim()
        manga.thumbnail_url = element.select("a").attr("style").substringAfter("('").substringBeforeLast("'")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = element.select("img").attr("data-src")
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("href")
        manga.title = element.select("a").text().trim()
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.select("td.table__chapter").select("a").attr("href")
        chapter.name = element.select("td.table__chapter").select("span").text().trim()
        chapter.chapter_number = element.select("td.table__chapter").select("span").text().substringAfter("Chapitre").replace(Regex("""[,-]"""), ".").trim().toFloatOrNull()
            ?: -1F
        chapter.date_upload = element.select("td.table__date").firstOrNull()?.text()?.let { parseDate(it) } ?: 0
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0L
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("div.manga-view__header-image").select("img").attr("abs:src")
        manga.description = document.select("dd.text-justify.text-break").text()
        manga.author = document.select("a[href*=author]").text()
        manga.artist = document.select("a[href*=artist]").text()
        val glist = document.select("a[href*=category]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span.badge.bg-success.text-uppercase").text()) {
            "En Cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        var div = body.select("div.text-center")
        var elements = div.select("img[id][src][data-src]")

        val pages = mutableListOf<Page>()
        for (i in 0 until elements.count()) {
            pages.add(Page(i, "", elements[i].attr("data-src").trim()))
        }
        return pages
    }
    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")
    override fun imageUrlParse(document: Document): String = throw Exception("Not used")
}
