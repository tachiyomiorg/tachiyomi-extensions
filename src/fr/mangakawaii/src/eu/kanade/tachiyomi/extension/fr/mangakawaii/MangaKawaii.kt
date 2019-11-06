package eu.kanade.tachiyomi.extension.fr.mangakawaii


import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.*


class MangaKawaii : ParsedHttpSource() {

    override val name = "Mangakawaii"
    override val baseUrl = "https://www.mangakawaii.to"
    override val lang = "fr"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "a.manga-block-item__content"
    override fun latestUpdatesSelector() = throw Exception("Not Used")
    override fun searchMangaSelector() = "h1 a"
    override fun chapterListSelector() = "div.chapter-item.volume-0, div.chapter-item.volume-"

    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun latestUpdatesNextPageSelector() = throw Exception("Not Used")
    override fun searchMangaNextPageSelector() = "no selector"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/filterLists?page=$page&sortBy=views&asc=false", headers)
    override fun latestUpdatesRequest(page: Int) = throw Exception("Not Used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search?query=$query")?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("abs:href"))
        manga.title = element.select("h3").text().trim()
        manga.thumbnail_url = element.select("a").attr("abs:data-background-image")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not Used")
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
        chapter.chapter_number = element.select("a.list-item__title").text().substringAfter("Chapitre").replace(",",".").trim().toFloat()
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
        val match = regex.findAll(element)!!.map { it.value.substringAfter("data-src\", \" ").substringBefore("\"").trim() }
        //throw Exception(match.elementAt(1))
        val pages = mutableListOf<Page>()
        for (i in 0 until match.count()) {
            pages.add(Page(i, "", match.elementAt(i)))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")


    override fun imageUrlParse(document: Document): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
