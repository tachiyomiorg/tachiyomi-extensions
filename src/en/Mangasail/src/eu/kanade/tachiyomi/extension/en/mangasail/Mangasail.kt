package eu.kanade.tachiyomi.extension.en.mangasail

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.Jsoup.parse
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.JsonObject

class Mangasail : ParsedHttpSource() {

    override val name = "Mangasail"

    override val baseUrl = "https://www.mangasail.co"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    /* Site loads some mannga info (manga cover, author name, status, etc.) client side through JQuery
    need to add this header for when we request these data fragments
    Also necessary for latest updates request */
    override fun headersBuilder() = super.headersBuilder().add("X-Authcache", "1")!!

    override fun popularMangaSelector() = "tbody tr"

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            return GET("$baseUrl/directory/hot")
        } else {
            return GET("$baseUrl/directory/hot?page=" + (page - 1))

        }
    }

    override fun latestUpdatesSelector() = "ul#latest-list > li"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/sites/all/modules/authcache/modules/authcache_p13n/frontcontroller/authcache.php?r=frag/block/showmanga-lastest_list&o[q]=node", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("td:first-of-type a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("td img").first().attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.title = element.select("a strong").text()
        element.select("a:has(img)").let {
            manga.url = it.attr("href")
            // Thumbnails are kind of low-res on latest updates page, transform the img url to get a better version
            manga.thumbnail_url = it.select("img").first().attr("src").substringBefore("?").replace("styles/minicover/public/", "")
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "table + div.text-center ul.pagination li.next a"

    override fun latestUpdatesNextPageSelector(): String = "There is no next page"

     override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) {
            return GET("$baseUrl/search/node/$query")
        } else {
            return GET("$baseUrl/search/node/$query?page=" + (page - 1))
        }
    }

    override fun searchMangaSelector() = "h3.title"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
            // Search page doesn't contain cover images, have to get them from the manga's page; but first we need that page's node number
            val node = getNodeNumber(client.newCall(GET(it.attr("href"), headers)).execute().asJsoup())
            manga.thumbnail_url = getNodeDetail(node, "field_image2")
        }
        return manga
    }

    // Function to get data fragments from website
    private fun getNodeDetail(node: String, field:String): String {
        val requestUrl = "$baseUrl/sites/all/modules/authcache/modules/authcache_p13n/frontcontroller/authcache.php?a[field][0]=$node:full:en&r=asm/field/node/$field&o[q]=node/$node"
        val call = client.newCall(GET(requestUrl, headers)).execute()
        val gson = Gson()
        if (call.isSuccessful) {
            when (field) {
                "field_image2" -> return gson.fromJson<JsonObject>(call.body()!!.string())["field"]["$node:full:en"].asString.substringAfter("src=\"").substringBefore("\"")
                "field_status", "field_author", "field_artist" -> return gson.fromJson<JsonObject>(call.body()!!.string())["field"]["$node:full:en"].asString.substringAfter("even\">").substringBefore("</div>")
                "body" -> return parse(gson.fromJson<JsonObject>(call.body()!!.string())["field"]["$node:full:en"].asString, baseUrl).select("p").text().substringAfter("summary: ")
                "field_genres" -> return parse(gson.fromJson<JsonObject>(call.body()!!.string())["field"]["$node:full:en"].asString, baseUrl).select("a").text()
            }
        }
            return ""
    }

    // Get a page's node number so we can get data fragments for that page
    private fun getNodeNumber (document: Document) : String {
        return document.select("[rel=shortlink]").attr("href").split("/").last().replace("\"", "")
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // On source's website most of these details are loaded through JQuery
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.main-content-inner")
        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()

        val node = getNodeNumber(document)
        manga.author = getNodeDetail(node, "field_author")
        manga.artist = getNodeDetail(node, "field_artist")
        manga.genre = getNodeDetail(node, "field_genres")
        val status = getNodeDetail(node, "field_status")
        manga.status = parseStatus(status)
        manga.description = getNodeDetail(node, "body")
        manga.thumbnail_url = getNodeDetail(node, "field_image2")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("a").text()
        chapter.date_upload = parseChapterDate(element.select("td + td").text()) ?: 0
        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("d MMM yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long? {
            return dateFormat.parse(string.substringAfter("on ")).time
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#images img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
