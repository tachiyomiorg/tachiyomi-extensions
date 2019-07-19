package eu.kanade.tachiyomi.extension.en.manmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.network.asObservableSuccess
import okhttp3.OkHttpClient
import rx.Observable
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat

class ManManga : ParsedHttpSource() {
    override val name = "Man Manga"

    override val baseUrl = "https://m.manmanga.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MMM dd, yyyy")
        }
    }


    override fun popularMangaSelector() = "#scrollBox > #scrollContent > li > a"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/category?sort=hot", headers)

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/category?sort=new", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
        = GET("$baseUrl/search?keyword=$query", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("li > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = element.select("div.text > h4").text()
        }
        return manga
    }

//    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
//        setUrlWithoutDomain(element.attr("href"))
//        title = element.select("div.text > h4").text().trim()
//    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element) : SManga {
        val manga = SManga.create()
        element.select("li > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = element.select("div.text > div.name > h4").text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = null

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response, query)
                }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply{
        val basicInfoElement = document.select("div.pro-box > div.relative-box")
        val moreInfoElement = document.select("div.about")
        val getThumbnailUrl = basicInfoElement.select("div.bg-box > div.bg").attr("style")

        author = moreInfoElement.select("div.types > div.author").text().replace("Author:","").trim()
        genre = basicInfoElement.select("div.info > div.tags > span").map {
            it.text().trim()
        }.joinToString(", ")
        status = moreInfoElement.select("div.types > div.type").text().replace("Status:","").trim().let {
            parseStatus(it)
        }
        description = moreInfoElement.select("div.synopsis > div.text > div.inner-text").text().trim()
        thumbnail_url = getThumbnailUrl.substring( getThumbnailUrl.indexOf("https://"), getThumbnailUrl.indexOf(")") )
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var response = response
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()
        document.select(chapterListSelector()).forEach {
            chapters.add(chapterFromElement(it))
        }
        return chapters
    }

    override fun chapterListSelector() = "div.chapter-list > dd > ul > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("data-num"))
        name = element.select("a").attr("alt").trim()
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        if(document.select("ul.img-list > li.unloaded > img").toString().isNotEmpty()) {
            document.select("ul.img-list > li.unloaded > img").forEach {
                val imgUrl = it.attr("data-src")
                pages.add(Page(pages.size, "", "$imgUrl"))
            }
        } else  {
            document.select("ul.img-list > li.loaded > img").forEach {
                val imgUrl = it.attr("data-src")
                pages.add(Page(pages.size, "", "$imgUrl"))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
