package eu.kanade.tachiyomi.extension.en.mangarawclub

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class MangaRawClub : ParsedHttpSource() {

    override val name = "manga-raw.club"
    override val baseUrl = "https://www.manga-raw.club"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse/?results=$page&filter=views", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/listy/manga/?results=$page", headers)
    }

    override fun popularMangaSelector() = "ul.novel-list.grid.col.col2 > li"

    // override fun popularMangaSelector() = "li.novel-item"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = "ul.novel-list.grid.col.col1 > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val coverElement = element.getElementsByClass("novel-cover").first()
        var titleElement = element.getElementsByClass("novel-title text1row").first()
        if (titleElement == null) {
            titleElement = element.getElementsByClass("novel-title text2row").first()
        }
        manga.thumbnail_url = coverElement.select("img").attr("data-src")
        manga.setUrlWithoutDomain(element.select("a").first().attr("href"))
        manga.title = titleElement.text()
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "ul.pagination > li:last-child > a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val authorElement = document.getElementsByClass("author").first()
        manga.author = authorElement.select("a").attr("title")
        manga.artist = ""
        val genres = mutableListOf<String>()
        document.select("a[href*=genre]").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        val status = when {
            document.select("div.header-stats").select("strong.completed").first() != null -> SManga.COMPLETED
            document.select("div.header-stats").select("strong.ongoing").first() != null -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        manga.status = status
        manga.description = document.getElementsByClass("description").first().text()
        manga.description = document.select("div.summary > div.content").first().text()
        val coverElement = document.getElementsByClass("cover")
        manga.thumbnail_url = baseUrl + coverElement.select("img").attr("data-src")

        return manga
    }

    override fun chapterListSelector() = "ul.chapter-list > li"

    override fun chapterFromElement(element: Element): SChapter {

        val urlElement = element.getElementsByTag("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.getElementsByClass("chapter-title").first().text()
        val date = element.getElementsByTag("time").first().attr("datetime")
        chapter.date_upload = parseChapterDate(date)
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        // "April 21, 2021, 4:05 p.m."
        val fdate = date.replace(".", "").replace("Sept", "Sep")
        val format = "MMMMM dd, yyyy, h:mm a"
        val format2 = "MMMMM dd, yyyy, h a" // because sometimes if it is exact hour it wont have minutes because why not
        val sdf = SimpleDateFormat(format, Locale.ENGLISH)
        try {
            return try {
                val value = sdf.parse(fdate)
                value!!.time
            } catch (e: ParseException) {
                val sdfF = SimpleDateFormat(format2, Locale.ENGLISH)
                val value = sdfF.parse(fdate)
                value!!.time
            }
        } catch (e: ParseException) {
            return 0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("section.page-in.content-wrap > div > center > img").forEachIndexed { i, it ->
            pages.add(Page(i, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {

        val request = searchMangaRequest(page, query, filters)
        return client.newCall(request).asObservableSuccess().map {
            response ->
            queryParse(response)
        }
    }

    private fun queryParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()
        document.select(latestUpdatesSelector()).forEach { element ->
            mangas.add(latestUpdatesFromElement(element))
        }
        val nextPage = document.select(latestUpdatesNextPageSelector()).first() != null
        return MangasPage(mangas, nextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {

        if (query.length > 0) {
            return GET("$baseUrl/search/?search=$query", headers)
        }

        val url = "$baseUrl/browse/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("results", page.toString())
        val requestBody = FormBody.Builder()

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {

                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.name)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            requestBody.add("options[]", genre)
                        }
                    }
                }
                is GenrePairList -> url.addQueryParameter("genre", filter.toUriPart())
                is Order -> url.addQueryParameter("filter", filter.toUriPart())
                is Status -> requestBody.add("status", filter.toUriPart())
                is Action -> requestBody.add("action", filter.toUriPart())
            }
        }

        return GET(url.toString(), headers)

        val built = requestBody.build()
        return POST("$baseUrl/search", headers, requestBody.build())
        // url: String,
        //         headers: Headers = DEFAULT_HEADERS,
        //         body: RequestBody = DEFAULT_BODY,
        //         cache: CacheControl = DEFAULT_CACHE_CONTROL)

        // return GET(url.toString(), headers)
    }

    private class Action : UriPartFilter(
        "Action",
        arrayOf(
            Pair("All", ""),
            Pair("Include", "include"),
            Pair("Exclude", "exclude")
        )
    )

    private class Order : UriPartFilter(
        "Order",
        arrayOf(
            Pair("Random", "Random"),
            Pair("Updated", "Updated"),
            Pair("New", "New"),
            Pair("Views", "views"),
        )
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private class Status : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Completed", "Completed"),
            Pair("Ongoing", "Ongoing")
        )
    )

    private class GenrePairList : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("All", ""),
            Pair("Action", "Action"),
            Pair("Adult", "Adult"),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Cooking", "Cooking"),
            Pair("Doujinshi", "Doujinshi"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Gender bender", "Gender bender"),
            Pair("Harem", "Harem"),
            Pair("Historical", "Historical"),
            Pair("Horror", "Horror"),
            Pair("Isekai", "Isekai"),
            Pair("Josei", "Josei"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("Martial arts", "Martial arts"),
            Pair("Mature", "Mature"),
            Pair("Mecha", "Mecha"),
            Pair("Medical", "Medical"),
            Pair("Mystery", "Mystery"),
            Pair("One shot", "One shot"),
            Pair("Psychological", "Psychological"),
            Pair("Romance", "Romance"),
            Pair("School life", "School life"),
            Pair("Sci fi", "Sci fi"),
            Pair("Seinen", "Seinen"),
            Pair("Shoujo", "Shoujo"),
            Pair("Shounen", "Shounen"),
            Pair("Slice of life", "Slice of life"),
            Pair("Sports", "Sports"),
            Pair("Supernatural", "Supernatural"),
            Pair("Tragedy", "Tragedy"),
            Pair("Webtoons", "Webtoons"),
            Pair("ladies", "ladies")
        )
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        // Status(),
        // Action(),
        Order(),
        GenrePairList(),
        // GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("Action"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Comedy"),
        Genre("Cooking"),
        Genre("Doujinshi"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender bender"),
        Genre("Harem"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("Isekai"),
        Genre("Josei"),
        Genre("Manhua"),
        Genre("Manhwa"),
        Genre("Martial arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Medical"),
        Genre("Mystery"),
        Genre("One shot"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School life"),
        Genre("Sci fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shounen"),
        Genre("Slice of life"),
        Genre("Sports"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Webtoons"),
        Genre("ladies")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
