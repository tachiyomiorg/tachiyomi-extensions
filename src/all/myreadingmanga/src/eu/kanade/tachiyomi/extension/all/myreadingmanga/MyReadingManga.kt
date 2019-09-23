package eu.kanade.tachiyomi.extension.all.myreadingmanga
import android.net.Uri
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

open class MyReadingManga(override val lang: String) : ParsedHttpSource() {

    override val name = "MyReadingManga"

    override val baseUrl = "https://myreadingmanga.info"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()!!

    override val supportsLatest = false

    override fun popularMangaSelector() = "article"

    override fun latestUpdatesSelector() = ""

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page/", headers)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        val list  = document.select(popularMangaSelector()).filter { element ->
            val select = element.select("a[rel=bookmark]")
            select.text().contains("[$lang", true)
        }
        for (element in list) {
            mangas.add(popularMangaFromElement(element))

        }

        val hasNextPage = popularMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element) = buildManga(element.select("a[rel]").first(), element.select("a.entry-image-link img").first())

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "li.pagination-next"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {

        val query2 = URLEncoder.encode(query, "UTF-8")
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search/").buildUpon()
                .appendEncodedPath(query2)
        } else {
            val uri = Uri.parse("$baseUrl/").buildUpon()
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri
        }
        uri.appendPath("page").appendPath("$page")
        return GET(uri.toString(), headers)
    }


    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    private fun buildManga(titleElement: Element, thumbnailElement: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(titleElement.attr("href"))
        manga.title = cleanTitle(titleElement.text())
        manga.thumbnail_url = getThumbnail(getImage(thumbnailElement))
        return manga
    }

    private fun getImage(element: Element): String {
        var url =
                when {
                    element.attr("data-src").endsWith(".jpg") || element.attr("data-src").endsWith(".png") || element.attr("data-src").endsWith(".jpeg") -> element.attr("data-src")
                    element.attr("src").endsWith(".jpg") || element.attr("src").endsWith(".png") || element.attr("src").endsWith(".jpeg") -> element.attr("src")
                    else -> element.attr("data-lazy-src")
                }
        if (url.startsWith("//")) {
            url = "http:$url"
        }
        return url
    }

    //removes resizing
    private fun getThumbnail(thumbnailUrl: String) = thumbnailUrl.substringBeforeLast("-") + "." + thumbnailUrl.substringAfterLast(".")

    //cleans up the name removing author and language from the title
    private fun cleanTitle(title: String) = title.substringBeforeLast("[").substringAfterLast("]").substringBeforeLast("(")
    private fun cleanAuthor(title: String) = title.substringAfter("[").substringBefore("]")

    override fun mangaDetailsParse(document: Document): SManga {
        val postid = document.select("article[class*=post-]").attr("class").substringBefore(" ").substringAfter("-") //Finds post ID for API lookup
        val mrmjson = client.newCall(GET("$baseUrl/wp-json/wp/v2/posts/$postid?_embed", headers)).execute() //Wordpress API lookup
        val jsonData = mrmjson.body()!!.string() //Convert Responce to string?
        val json = JsonParser().parse(jsonData).asJsonObject //Convert string to Json Object?
        var thumbnailUrl :String? = "" // Int varable
        val reststatus = json["data"].nullObj //MRM throws error 401 for API if not logged in as user
        if (reststatus != null ) {} else { //Only look for embedded data when logged in. If not, throws error that json[embedded] is not found
            val mediaobj = json["_embedded"]["wp:featuredmedia"][0].obj //Somehow limited where a retreive object is unable to follow a retreive arrary
            thumbnailUrl = mediaobj["source_url"].string
        }
        val manga = SManga.create()
        manga.author = cleanAuthor(document.select("h1").text())
        manga.artist = cleanAuthor(document.select("h1").text())
        val glist = document.select(".entry-header p a[href*=genre]").map { it -> it.text() }
        manga.genre = glist.joinToString(", ")
        manga.description = document.select("h1").text() + "\n" + document.select(".info-class")?.text()
        manga.status = when (document.select("a[href*=status]")?.first()?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        if (reststatus != null ) {} else {manga.thumbnail_url=thumbnailUrl} //Sets the thumbnail when logged in to API
        return manga
    }

    override fun chapterListSelector() = ".entry-pagination a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val date = parseDate(document.select(".entry-time").text())
        val mangaUrl = document.baseUri()
        val chfirstname = document.select(".chapter-class a[href*=$mangaUrl]")?.first()?.text()?.ifEmpty { "Ch. 1" }?.capitalize() ?:"Ch. 1"
        val scangroup= document.select(".entry-terms a[href*=group]")?.first()?.text()
        //create first chapter since its on main manga page
        chapters.add(createChapter("1", document.baseUri(), date, chfirstname, scangroup))
        //see if there are multiple chapters or not
        document.select(chapterListSelector())?.let { it ->
            it.forEach {
                if (!it.text().contains("Next »", true)) {
                    val pageNumber = it.text()
                    val chname = document.select(".chapter-class a[href$=/$pageNumber/]")?.text()?.ifEmpty { "Ch. $pageNumber" }?.capitalize() ?:"Ch. $pageNumber"
                    chapters.add(createChapter(it.text(), document.baseUri(), date, chname, scangroup))
                }
            }
        }
        chapters.reverse()

        return chapters
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US ).parse(date).time
    }

    private fun createChapter(pageNumber: String, mangaUrl: String, date: Long, chname: String, scangroup: String?): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("$mangaUrl/$pageNumber")
        chapter.name = chname
        chapter.date_upload = date
        chapter.scanlator = scangroup
        return chapter
    }

    override fun searchMangaNextPageSelector() = throw Exception("Not used")


    override fun chapterFromElement(element: Element) = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        val pages = mutableListOf<Page>()
        val elements = body.select("img[data-lazy-src]:not([width='120']):not([data-original-width='300'])")
        for (i in 0 until elements.size) {
            pages.add(Page(i, "", getImage(elements[i])))
        }
        return pages
    }

    override fun pageListParse(document: Document) = throw Exception("Not used")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    //Filter Parsing, grabs home page as document and filters out Genres, Popular Tags, and Catagorys
    private val filterdoc = OkHttpClient().newCall(GET("$baseUrl", headers)).execute().asJsoup()
    private val genresarray = filterdoc.select(".tagcloud a[href*=/genre/]").map { Pair(it.attr("href").substringBeforeLast("/").substringAfterLast("/"), it.text())}.toTypedArray()
    private val poptagarray = filterdoc.select(".tagcloud a[href*=/tag/]").map { Pair(it.attr("href").substringBeforeLast("/").substringAfterLast("/"), it.text())}.toTypedArray()
    private val cattagarray = filterdoc.select(".level-0").map { Pair(it.attr("value"), it.text())}.toTypedArray()
    
    //Generates the filter lists for app
    override fun getFilterList(): FilterList {
        val filterList = FilterList(
            //MRM does not support genre filtering and text search at the same time
            Filter.Header("NOTE: Filters are ignored if using text search."),
            Filter.Header("Only one filter can be used at a time."),
            GenreFilter(genresarray),
            TagFilter(poptagarray),
            CatFilter(cattagarray)
        )
        return filterList
    }

    private class GenreFilter(GENRES: Array<Pair<String, String>>) : UriSelectFilterPath("Genre", "genre", arrayOf(Pair("","Any"),*GENRES))
    private class TagFilter(POPTAG: Array<Pair<String, String>>) : UriSelectFilterPath("Popular Tags", "tag", arrayOf(Pair("","Any"),*POPTAG))
    private class CatFilter(CATID: Array<Pair<String, String>>) : UriSelectFilterQuery("Categories", "cat", arrayOf(Pair("","Any"), *CATID))

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilterPath(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                       val firstIsUnspecified: Boolean = true,
                                       defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendPath(uriParam)
                    .appendPath(vals[state].first)
        }
    }
    private open class UriSelectFilterQuery(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                       val firstIsUnspecified: Boolean = true,
                                       defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
    
}
