package eu.kanade.tachiyomi.extension.en.mangafree

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mangafree : ParsedHttpSource() {

    override val name = "Mangafree"

    override val baseUrl = "http://mangafree.online"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.truyen-list > div.list-truyen-item-wrap"

    override fun popularMangaRequest(page: Int): Request {

        return GET("$baseUrl/hotmanga/$page")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/$page")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        return MangasPage(mangas, hasNextPage(document))
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        return MangasPage(mangas, hasNextPage(document))
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //site ignore everything after the first word
        val substringBefore = query.substringBefore(" ")
        val url = "$baseUrl/search/$substringBefore/$page"
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()


        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        return MangasPage(mangas, hasNextPage(document))
    }

    private fun hasNextPage(document: Document): Boolean {
        val pagiNag = document.getElementsByTag("script").map {
            it.data().trim()
        }.find {
            it.contains("//<![CDATA[") && it.contains("setPagiNation(")
        } ?: throw IllegalArgumentException("Cannot find pages")
        val list = pagiNag.substringAfter("setPagiNation(").substringBefore(",'").split(",")
        return list[0] != list[1]
    }


    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.manga-info-top").first()


        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()
        manga.author = infoElement.select("div.manga-info-top li a#ContentPlaceHolderLeft_mA_Actor").text()
        manga.status = parseStatus(infoElement.select("div.manga-info-top li span#ContentPlaceHolderLeft_span_status").text())

        val genres = mutableListOf<String>()
        infoElement.select("div.manga-info-top li p#ContentPlaceHolderLeft_mP_Kind a").forEach { it -> genres.add(it.text()) }
        manga.genre = genres.joinToString()
        manga.description = document.select("div#noidungm div#ContentPlaceHolderLeft_maincontent").text()
        manga.thumbnail_url = document.select("div.manga-info-pic").first().select("img").first().attr("src")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.chapter-list div.row"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        return chapter
    }


    override fun pageListParse(document: Document): List<Page> {

        val pages = mutableListOf<Page>()

        document.select("div#vungdoc img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("No used")

    override fun getFilterList() = FilterList()


}
