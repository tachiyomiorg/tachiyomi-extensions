package eu.kanade.tachiyomi.extension.en.mangacruzers

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class mangacruzers : ParsedHttpSource() {

    override val name = "Manga Cruzers"
    override val baseUrl = "https://ww2.mangacruzers.com"
    override val lang = "en"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    override fun popularMangaSelector() = "tr"//"td > a:not(a[href*=Cruzers])"
    override fun latestUpdatesSelector() = throw Exception ("Not Used")
    override fun searchMangaSelector() = popularMangaSelector()
    override fun chapterListSelector() = "div.flex.items-center > div.flex.flex-col:not(.items-center), tbody.no-border-x > tr"

    override fun popularMangaNextPageSelector() = "none"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/read-manga/", headers)
    override fun latestUpdatesRequest(page: Int) = throw Exception ("Not Used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception ("Not Used")


    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        val element = document.select(popularMangaSelector())
        for (i in 1 until element.size-2) {
            mangas.add(mangaFromElement(element[i]))
        }
        return MangasPage(mangas, false)

    }

    override fun mangaDetailsRequest(manga: SManga) = chapterListRequest(manga)
    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)
    override fun chapterListRequest(manga: SManga): Request {
        val document = client.newCall(GET(manga.url, headers)).execute().asJsoup()
        val select = document.select("a:containsOwn(View all chapters)")
        val url = if (!select.isNullOrEmpty()) {
            select.first().attr("href")
        } else {
            manga.url
        }
        return GET(url, headers)
    }
    //override fun chapterListRequest(manga: SManga) = GET(manga.url + "/comic/archive", headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element)= mangaFromElement(element)

        private fun mangaFromElement(element: Element): SManga {
            Log.i("TachiDebug","MangaElement => $element")
            val manga = SManga.create()
                manga.url = element.select("a").attr("abs:href")
                Log.i("TachiDebug", "MangaURL => ${manga.url}")
                manga.title = element.select("td").first().text().trim()
                //manga.thumbnail_url = element.select("img").attr("abs:src")
                //manga.artist = element.select("h2").text().removePrefix("by").trim()
                //manga.author = manga.artist
                //manga.description = element.select("div.description").text().trim() + "\n" + "\n" + "*Not all comics are supported*"
                //manga.genre = element.select("div.comicrating").text().trim()
        return manga
    }
/*
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val uri = Uri.parse(document.baseUri())
        val baseUrl = "${uri.scheme}://${uri.authority}"
        Log.i("TachiDebug", "BaseURL => $baseUrl")
        val elements = document.select(chapterListSelector())
        val chapters = mutableListOf<SChapter>()
        for (i in 1 until elements.size) {
            chapters.add(createChapter(elements[i] , baseUrl))
        }
        chapters.reverse()
        return chapters
    }

    private fun createChapter(element: Element, baseUrl: String?) = SChapter.create().apply {
        name = element.text().substringAfter("-").trim()
        url = "$baseUrl/" + element.attr("value")
        Log.i("TachiDebug","Chapter URL => $url")
        date_upload = parseDate(element.text().substringBefore("-").trim())
    }


 */

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US ).parse(date).time
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = element.select("a").first().attr("abs:href")
        val td = element.select("td")
        if (!td.isNullOrEmpty()) {
            name = td[0].text()
            date_upload = parseDate(td[1].text())
        } else {
            val substring = element.select("div.text-xs").text()
            name = element.select("a").text() + if (!substring.isNullOrBlank()) { " - $substring" } else {""}
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h1.text-white").text().removePrefix("READ").removeSuffix("ONLINE").trim()
        manga.thumbnail_url = document.select("img.border-8, img.card-img-top, img.card-img-right").attr("src")
        manga.description = document.select("div:containsOwn(Description) + div, div.align-items-start > p:eq(2)").text()
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img.my-3, img.pages__img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }
    
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")
}


