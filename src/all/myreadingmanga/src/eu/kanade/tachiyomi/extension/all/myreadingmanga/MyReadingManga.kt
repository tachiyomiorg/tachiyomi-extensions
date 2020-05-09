package eu.kanade.tachiyomi.extension.all.myreadingmanga

import android.annotation.SuppressLint
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class MyReadingManga(override val lang: String, private val siteLang: String, private val latestLang: String) : ParsedHttpSource() {

    // Basic Info
    override val name = "MyReadingManga"
    final override val baseUrl = "https://myreadingmanga.info"
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!
    override val supportsLatest = true

    // Popular - Random
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search/?wpsolr_sort=sort_by_random&wpsolr_page=$page&wpsolr_fq[0]=lang_str:$siteLang", headers) // Random Manga as returned by search
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (!filtersCached) {
            cachedPagesUrls.onEach { filterAssist(it.value) }
            filtersCached = true
        }
        return searchMangaParse(response)
    }
    override fun popularMangaNextPageSelector() = throw Exception("Not used")
    override fun popularMangaSelector() = throw Exception("Not used")
    override fun popularMangaFromElement(element: Element) = throw Exception("Not used")

    // Latest
    @SuppressLint("DefaultLocale")
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/lang/${latestLang.toLowerCase()}" + if (page > 1) "/page/$page/" else "", headers) // Home Page - Latest Manga
    }

    override fun latestUpdatesNextPageSelector() = "li.pagination-next"
    override fun latestUpdatesSelector() = "article"
    override fun latestUpdatesFromElement(element: Element) = buildManga(element.select("a[rel]").first(), element.select("a.entry-image-link img").first())

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var fqIndex = 0
        val uri = Uri.parse("$baseUrl/search/").buildUpon()
            .appendQueryParameter("wpsolr_q", query)
            .appendQueryParameter("wpsolr_fq[$fqIndex]", "lang_str:$siteLang")
            .appendQueryParameter("wpsolr_page", page.toString())
        filters.forEach {
            if (it is UriFilter) {
                fqIndex++
                it.addToUri(uri, "wpsolr_fq[$fqIndex]")
            }
        }
        return GET(uri.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")
    override fun searchMangaSelector() = "div.results-by-facets div[id*=res]"
    private var mangaParsedSoFar = 0
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.location().contains("page=1")) mangaParsedSoFar = 0
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
            .also { mangaParsedSoFar += it.count() }
        val totalResults = Regex("""(\d+)""").find(document.select("div.res_info").text())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return MangasPage(mangas, mangaParsedSoFar < totalResults)
    }
    override fun searchMangaFromElement(element: Element) = buildManga(element.select("a").first(), element.select("img")?.first())

    // Build Manga From Element
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

    // removes resizing
    private fun getThumbnail(thumbnailUrl: String) = thumbnailUrl.substringBeforeLast("-") + "." + thumbnailUrl.substringAfterLast(".")

    // cleans up the name removing author and language from the title
    private fun cleanTitle(title: String) = title.substringBeforeLast("[").substringAfterLast("]").substringBeforeLast("(").trim()

    private fun cleanAuthor(author: String) = author.substringAfter("[").substringBefore("]").trim()

    // Manga Details
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
        manga.genre = document.select(".entry-header p a[href*=genre]").joinToString(", ") { it.text() }
        val extendedDescription = document.select(".entry-content p:not(p:containsOwn(|)):not(.chapter-class + p)")?.joinToString("\n") { it.text() }
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

    // Start Chapter Get
    override fun chapterListSelector() = ".entry-pagination a"

    @SuppressLint("DefaultLocale")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val date = parseDate(document.select(".entry-time").text())
        val mangaUrl = document.baseUri()
        val chfirstname = document.select(".chapter-class a[href*=$mangaUrl]")?.first()?.text()?.ifEmpty { "Ch. 1" }?.capitalize()
            ?: "Ch. 1"
        val scangroup = document.select(".entry-terms a[href*=group]")?.first()?.text()
        // create first chapter since its on main manga page
        chapters.add(createChapter("1", document.baseUri(), date, chfirstname, scangroup))
        // see if there are multiple chapters or not
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

    // Pages
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

    // Filter Parsing, grabs pages as document and filters out Genres, Popular Tags, and Categories, Parings, and Scan Groups
    private var filtersCached = false

    // Grabs page containing filters and puts it into cache
    private fun filterAssist(url: String): String {
        val response = client.newCall(GET(url, headers)).execute()
        return response.body()!!.string()
    }

    // Returns page from cache to reduce calls to website
    private fun getCache(url: String): Document? {
        val response = client.newCall(GET(url, headers, CacheControl.FORCE_CACHE)).execute()
        return if (response.isSuccessful) {
            filtersCached = true
            response.asJsoup()
        } else {
            filtersCached = false
            null
        }
    }

    // Parses page for filter
    private fun returnFilter(document: Document?, css: String): Array<String> {
        return document?.select(css)?.map { it.text() }?.toTypedArray()
            ?: arrayOf("Press 'Reset' to try again")
    }

    // URLs for the pages we need to cache
    private val cachedPagesUrls = hashMapOf(
        Pair("genres", baseUrl),
        Pair("tags", baseUrl),
        Pair("categories", "$baseUrl/cats/"),
        Pair("pairings", "$baseUrl/pairing/"),
        Pair("groups", "$baseUrl/group/")
    )

    // Generates the filter lists for app
    override fun getFilterList(): FilterList {
        return FilterList(
            GenreFilter(returnFilter(getCache(cachedPagesUrls["genres"]!!), ".tagcloud a[href*=/genre/]")),
            TagFilter(returnFilter(getCache(cachedPagesUrls["tags"]!!), ".tagcloud a[href*=/tag/]")),
            CatFilter(returnFilter(getCache(cachedPagesUrls["categories"]!!), ".links a")),
            PairingFilter(returnFilter(getCache(cachedPagesUrls["pairings"]!!), ".links a")),
            ScanGroupFilter(returnFilter(getCache(cachedPagesUrls["groups"]!!), ".links a"))
        )
    }

    private class GenreFilter(GENRES: Array<String>) : UriSelectFilter("Genre", "genre_str", arrayOf("Any", *GENRES))
    private class TagFilter(POPTAG: Array<String>) : UriSelectFilter("Popular Tags", "tags", arrayOf("Any", *POPTAG))
    private class CatFilter(CATID: Array<String>) : UriSelectFilter("Categories", "categories", arrayOf("Any", *CATID))
    private class PairingFilter(PAIR: Array<String>) : UriSelectFilter("Pairing", "pairing_str", arrayOf("Any", *PAIR))
    private class ScanGroupFilter(GROUP: Array<String>) : UriSelectFilter("Scanlation Group", "group_str", arrayOf("Any", *GROUP))

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    private open class UriSelectFilter(
        displayName: String,
        val uriValuePrefix: String,
        val vals: Array<String>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder, uriParam: String) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, "$uriValuePrefix:${vals[state]}")
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder, uriParam: String)
    }
}
