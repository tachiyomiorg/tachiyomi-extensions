package eu.kanade.tachiyomi.extension.fr.mangakawaii


import android.net.Uri
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


class MangaKawaii : ParsedHttpSource() {

    override val name = "Mangakawaii"
    override val baseUrl = "https://www.mangakawaii.com"
    override val lang = "fr"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0")
    }

    override fun popularMangaSelector() = "a.manga-block-item__content"
    override fun latestUpdatesSelector() = ".manga-list li div.updates__left"
    override fun searchMangaSelector() = "h1 + ul a[href*=manga]"
    override fun chapterListSelector() = "div.chapter-item.volume-0, div.chapter-item.volume-"

    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun searchMangaNextPageSelector() = "no selector"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/filterLists?page=$page&sortBy=views&asc=false", headers)
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
            .appendQueryParameter("query", query)
        return GET(uri.toString(), headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("abs:href"))
        manga.title = element.select("h3").text().trim()
        manga.thumbnail_url = element.select("a").attr("abs:data-background-image")
        return manga
    }
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select(" a").attr("title")
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
        chapter.url = element.select("a.list-item__title").attr("href")
        chapter.name = element.select("a.list-item__title").text().trim()
        chapter.chapter_number = element.select("a.list-item__title").text().substringAfter("Chapitre").replace(",", ".").trim().toFloat()
        chapter.date_upload = parseDate(element.select("div.chapter-item__date").text())
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date).time
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img.manga__cover").attr("abs:src")
        manga.description = document.select("div.info-desc__content").text()
        manga.author = document.select("a[href*=author]").text()
        manga.artist = document.select("a[href*=artist]").text()
        val glist = document.select("a[href*=category]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span.label.label-success").text()) {
            "En Cours" -> SManga.ONGOING
            "Terminé" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        val element = body.select("script:containsData(Imagesrc)").toString()
        val regex = "(data-src).*[\"]".toRegex()
        val match = regex.findAll(element).map { it.value.substringAfter("data-src\", \" ").substringBefore("\"").trim() }
        //throw Exception(match.elementAt(1))
        val pages = mutableListOf<Page>()
        for (i in 0 until match.count()) {
            pages.add(Page(i, "", match.elementAt(i)))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")
    override fun imageUrlParse(document: Document): String = throw Exception("Not used")
}
