package eu.kanade.tachiyomi.extension.all.myreadingmanga

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

open class MyReadingManga(override val lang: String) : ParsedHttpSource() {

    //Basic Info
    override val name = "MyReadingManga"
    override val baseUrl = "https://myreadingmanga.info"
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!
    override val supportsLatest = true

    //Popular - Random
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search/?wpsolr_sort=sort_by_random&wpsolr_page=$page", headers) // Random Manga as returned by search
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    //Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page/", headers)     //Home Page - Latest Manga
    }

    override fun latestUpdatesNextPageSelector() = "li.pagination-next"
    override fun latestUpdatesSelector() = "article"
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        val list = document.select(latestUpdatesSelector()).filter { element ->
            val select = element.select("a[rel=bookmark]")
            select.text().contains("[$lang", true)
        }
        for (element in list) {
            mangas.add(latestUpdatesFromElement(element))

        }

        val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element) = buildManga(element.select("a[rel]").first(), element.select("a.entry-image-link img").first())

    //Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search/").buildUpon()
                .appendQueryParameter("search", query)
                .appendQueryParameter("wpsolr_page", page.toString())

        } else {
            val uri = Uri.parse("$baseUrl/").buildUpon()
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.appendPath("page").appendPath("$page")
        }
        return GET(uri.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector() = "div.results-by-facets div[id*=res]"
    override fun searchMangaParse(response: Response): MangasPage {
        //Filter Assist - Caches Pages required for filter parsing
        if (!filtesrCached) {
            filterAssist(baseUrl)
            filterAssist("$baseUrl/cats/")
            filterAssist("$baseUrl/pairing/")
            filterAssist("$baseUrl/group/")
            filtesrCached = true
        }

        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        //Process Search Results
        if (document.baseUri().contains("search", true)) {
            val elements = document.select(searchMangaSelector())
            for (element in elements) {
                if (element.text().contains("[$lang", true)) {
                    mangas.add(searchMangaFromElement(element))
                }
            }
            val hasNextPage = searchMangaSelector().let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        }
        //Process Filter Results / Same theme as home page
        else {
            //return popularMangaParse(response)
            val list = document.select(latestUpdatesSelector()).filter { element ->
                val select = element.select("a[rel=bookmark]")
                select.text().contains("[$lang", true)
            }
            for (element in list) {
                mangas.add(latestUpdatesFromElement(element))

            }

            val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        }
    }

    override fun searchMangaFromElement(element: Element) = buildManga(element.select("a").first(), element.select("img")?.first())


    //Build Manga From Element
    private fun buildManga(titleElement: Element, thumbnailElement: Element?): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(titleElement.attr("href"))
        manga.title = cleanTitle(titleElement.text())
        if (thumbnailElement != null) manga.thumbnail_url = getThumbnail(getImage(thumbnailElement))
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
            url = "https:$url"
        }
        return url
    }

    //removes resizing
    private fun getThumbnail(thumbnailUrl: String) = thumbnailUrl.substringBeforeLast("-") + "." + thumbnailUrl.substringAfterLast(".")

    //cleans up the name removing author and language from the title
    private fun cleanTitle(title: String) = title.substringBeforeLast("[").substringAfterLast("]").substringBeforeLast("(").trim()

    private fun cleanAuthor(author: String) = author.substringAfter("[").substringBefore("]").trim()


    //Manga Details
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val needCover = manga.thumbnail_url.isNullOrEmpty()

        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response.asJsoup(), needCover).apply { initialized = true }
            }
    }

    private fun mangaDetailsParse(document: Document, needCover: Boolean): SManga {
        val manga = SManga.create()
        manga.author = cleanAuthor(document.select("h1").text())
        manga.artist = cleanAuthor(document.select("h1").text())
        val glist = document.select(".entry-header p a[href*=genre]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        val extendedDescription = document.select(".entry-content p:not(p:containsOwn(|)):not(.chapter-class + p)")?.map { it.text() }?.joinToString("\n")
        manga.description = document.select("h1").text() + if (extendedDescription.isNullOrEmpty()) "" else "\n\n$extendedDescription"
        manga.status = when (document.select("a[href*=status]")?.first()?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        if (needCover) {
            manga.thumbnail_url = getThumbnail(client.newCall(GET("$baseUrl/search/?search=${document.location()}", headers))
                .execute().asJsoup().select("div.wdm_results div.p_content img").first().attr("abs:src"))
        }

        return manga
    }

    override fun mangaDetailsParse(document: Document) = throw Exception("Not used")


    //Start Chapter Get
    override fun chapterListSelector() = ".entry-pagination a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val date = parseDate(document.select(".entry-time").text())
        val mangaUrl = document.baseUri()
        val chfirstname = document.select(".chapter-class a[href*=$mangaUrl]")?.first()?.text()?.ifEmpty { "Ch. 1" }?.capitalize()
            ?: "Ch. 1"
        val scangroup = document.select(".entry-terms a[href*=group]")?.first()?.text()
        //create first chapter since its on main manga page
        chapters.add(createChapter("1", document.baseUri(), date, chfirstname, scangroup))
        //see if there are multiple chapters or not
        document.select(chapterListSelector())?.let { it ->
            it.forEach {
                if (!it.text().contains("Next »", true)) {
                    val pageNumber = it.text()
                    val chname = document.select(".chapter-class a[href$=/$pageNumber/]")?.text()?.ifEmpty { "Ch. $pageNumber" }?.capitalize()
                        ?: "Ch. $pageNumber"
                    chapters.add(createChapter(it.text(), document.baseUri(), date, chname, scangroup))
                }
            }
        }
        chapters.reverse()
        return chapters
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(date).time
    }

    private fun createChapter(pageNumber: String, mangaUrl: String, date: Long, chname: String, scangroup: String?): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("$mangaUrl/$pageNumber")
        chapter.name = chname
        chapter.date_upload = date
        chapter.scanlator = scangroup
        return chapter
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not used")

    //Pages
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

    //Filter Parsing, grabs pages as document and filters out Genres, Popular Tags, and Categories, Parings, and Scan Groups
    private var filtesrCached = false

    //Grabs page containing filters and puts it into cache
    private fun filterAssist(url: String): String {
        val response = client.newCall(GET(url, headers)).execute()
        return response.body()!!.string()
    }

    //Returns page from cache to reduce calls to website
    private fun getCache(url: String): Document? {
        val response = client.newCall(GET(url, headers, CacheControl.FORCE_CACHE)).execute()
        return if (response.isSuccessful) {
            filtesrCached = true
            response.asJsoup()
        } else {
            filtesrCached = false
            null
        }
    }

    //Parses page for filter
    private fun returnFilter(document: Document?, css: String, attributekey: String): Array<Pair<String, String>> {
        return document?.select(css)?.map { Pair(it.attr(attributekey).substringBeforeLast("/").substringAfterLast("/"), it.text()) }?.toTypedArray()
            ?: arrayOf(Pair("", "Press 'Reset' to try again"))
    }

    //Generates the filter lists for app
    override fun getFilterList(): FilterList {
        val filterList = FilterList(
            //MRM does not support genre filtering and text search at the same time
            Filter.Header("NOTE: Filters are ignored if using text search."),
            Filter.Header("Only one filter can be used at a time."),
            GenreFilter(returnFilter(getCache(baseUrl), ".tagcloud a[href*=/genre/]", "href")),
            TagFilter(returnFilter(getCache(baseUrl), ".tagcloud a[href*=/tag/]", "href")),
            CatFilter(returnFilter(getCache("$baseUrl/cats/"), ".links a", "abs:href")),
            PairingFilter(returnFilter(getCache("$baseUrl/pairing/"), ".links a", "abs:href")),
            ScanGroupFilter(returnFilter(getCache("$baseUrl/group/"), ".links a", "abs:href"))
        )
        return filterList
    }

    private class GenreFilter(GENRES: Array<Pair<String, String>>) : UriSelectFilterPath("Genre", "genre", arrayOf(Pair("", "Any"), *GENRES))
    private class TagFilter(POPTAG: Array<Pair<String, String>>) : UriSelectFilterPath("Popular Tags", "tag", arrayOf(Pair("", "Any"), *POPTAG))
    private class CatFilter(CATID: Array<Pair<String, String>>) : UriSelectFilterShortPath("Categories", "cat", arrayOf(Pair("", "Any"), *CATID))
    private class PairingFilter(PAIR: Array<Pair<String, String>>) : UriSelectFilterPath("Pairing", "pairing", arrayOf(Pair("", "Any"), *PAIR))
    private class ScanGroupFilter(GROUP: Array<Pair<String, String>>) : UriSelectFilterPath("Scanlation Group", "group", arrayOf(Pair("", "Any"), *GROUP))

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

    private open class UriSelectFilterShortPath(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                                val firstIsUnspecified: Boolean = true,
                                                defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendPath(vals[state].first)

        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

}

