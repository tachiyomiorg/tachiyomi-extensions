package eu.kanade.tachiyomi.extension.en.toonily

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import eu.kanade.tachiyomi.source.model.*
import java.text.ParseException

class Toonily: ParsedHttpSource() {

    override val name = "Toonily"
    override val baseUrl = "https://toonily.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    }

    override fun popularMangaSelector() = "div.c-tabs-item__content"
    override fun latestUpdatesSelector() =  popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =  searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga =  searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a#navigation-ajax.btn.btn-default.load-ajax"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element):SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.tab-thumb > a > img").attr("data-src")
        element.select("div.tab-thumb > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
        url.addQueryParameter("post_type","wp-manga")
        val pattern = "\\s+".toRegex()
        val q = query.replace(pattern, "+")
        if(query.isNotEmpty()){
            url.addQueryParameter("s", q)
        }else{
            url.addQueryParameter("s", "")
        }

        var orderBy = ""
        var condFilter = ""
        var adultFilter = ""

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state) {
                            genreInclude.add(it.id)
                        }
                    }
                    if(genreInclude.isNotEmpty()){
                        genreInclude.forEach{ genre ->
                            url.addQueryParameter("genre[]", genre)
                        }
                    }
                }
                is StatusList ->{
                    val statuses = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state) {
                            statuses.add(it.id)
                        }
                    }
                    if(statuses.isNotEmpty()){
                        statuses.forEach{ status ->
                            url.addQueryParameter("status[]", status)
                        }
                    }
                }

                is SortBy -> {
                    orderBy = filter.toUriPart()
                    url.addQueryParameter("m_orderby",orderBy)
                }
                is CondFilter -> {
                    condFilter = filter.toUriPart()
                    url.addQueryParameter("op",condFilter)
                }
                is AdultFilter -> {
                    adultFilter = filter.toUriPart()
                    url.addQueryParameter("adult",adultFilter)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }

        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.site-content").first()

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content")?.text()
        manga.artist = infoElement.select("div.artist-content")?.text()

        val genres = mutableListOf<String>()
        infoElement.select("div.genres-content a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre =genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("div.post-status > div:nth-child(2)  div").text())

        manga.description = document.select("div.summary__content > p")?.text()
        manga.thumbnail_url = document.select("div.summary_image > a > img").attr("data-src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {

        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("span.chapter-release-date i").last()?.text()?.let {
            try {
                SimpleDateFormat("MMMM dd, yyyy", Locale.US).parse(it).time
            } catch (e: ParseException) {
                SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(it).time
            }

        } ?: 0
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document) = document.select("div.reading-content * img").mapIndexed { i, element -> Page(i, "", element.attr("data-src")) }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
    //    private class Status : Filter.TriState("Completed")
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class SortBy : UriPartFilter("Sort by", arrayOf(
            Pair("Relevance", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Rating", "rating"),
            Pair("Trending", "trending"),
            Pair("Most View", "views"),
            Pair("New", "new-manga")
    ))
    private class CondFilter : UriPartFilter("Genres condition", arrayOf(
        Pair("OR (having one of selected genres)", ""),
        Pair("AND (having all selected genres)", "1")
    ))
    private class AdultFilter : UriPartFilter("Adult content", arrayOf(
        Pair("All", ""),
        Pair("None adult content", "0"),
        Pair("Only adult content", "1")
    ))
    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status(name: String, val id: String = name) : Filter.CheckBox(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    override fun getFilterList() = FilterList(
            //TextField("Judul", "title"),
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            TextField("Year", "release"),
            SortBy(),
            CondFilter(),
            AdultFilter(),
            StatusList(getStatusList()),
            GenreList(getGenreList())
    )
    private fun getStatusList() = listOf(
            Status("Completed","end"),
            Status("Ongoing","on-going"),
            Status("Canceled","canceled"),
            Status("Onhold","on-hold")
    )
    private fun getGenreList() = listOf(
            Genre("Action", "action-webtoon"),
            Genre("Adventure", "adventure-webtoon"),
            Genre("Comedy", "comedy-webtoon"),
            Genre("Drama", "drama-webtoon"),
            Genre("Fantasy", "fantasy-webtoon"),
            Genre("Harem", "harem-webtoon"),
            Genre("Horror", "horror-webtoon"),
            Genre("Mature", "mature-webtoon"),
            Genre("Mystery", "mystery-webtoon"),
            Genre("Psychological", "psychological-webtoon"),
            Genre("Romance", "romance-webtoon"),
            Genre("School life", "school-life-webtoon"),
            Genre("Sci-Fi", "scifi-webtoon"),
            Genre("Seinen", "seinen-webtoon"),
            Genre("Shounen", "shounen-webtoon"),
            Genre("Supernatural", "supernatural-webtoon"),
            Genre("Thriller", "thriller-webtoon"),
            Genre("Yaoi", "yaoi-webtoon"),
            Genre("Yuri", "yuri-webtoon")
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

}
