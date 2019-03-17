package eu.kanade.tachiyomi.extension.id.komikgo

import java.util.*
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import java.text.ParseException
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import eu.kanade.tachiyomi.source.model.*


class Komikgo : ParsedHttpSource() {

    override val name = "Komikgo"
    override val baseUrl = "https://komikgo.com"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    }
    //    LIST SELECTOR
    override fun popularMangaSelector() = "div.c-tabs-item__content"
    override fun latestUpdatesSelector() =  popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    //    ELEMENT
    override fun popularMangaFromElement(element: Element): SManga =  searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga =  searchMangaFromElement(element)

    //    NEXT SELECTOR
    override fun popularMangaNextPageSelector() = "#navigation-ajax"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element):SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.tab-thumb > a > img").attr("src")
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
        if(query.length > 0){
            url.addQueryParameter("s", q)
        }else{
            url.addQueryParameter("s", "")
        }

        var orderBy = ""

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
//                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
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
                        if (it.state == 1) {
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
                    orderBy = filter.toUriPart();
                    url.addQueryParameter("m_orderby",orderBy)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }
        Log.d("TAG", url.toString())

        return GET(url.toString(), headers)
    }



    // max 200 results

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

        manga.description = document.select("div.description-summary")?.text()
        manga.thumbnail_url = document.select("div.summary_image > a > img").attr("src")

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

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.reading-content * img").forEach { element ->
            val url = element.attr("src")
            i++
            if(url.length != 0){
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

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
    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    override fun getFilterList() = FilterList(
//            TextField("Judul", "title"),
            TextField("Author", "author"),
            TextField("Year", "release"),
            SortBy(),
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
            Genre("Adventure", "Adventure"),
            Genre( "Action",  "action"),
            Genre( "Adventure",  "adventure"),
            Genre( "Cars",  "cars"),
            Genre( "4-Koma",  "4-koma"),
            Genre( "Comedy",  "comedy"),
            Genre( "Completed",  "completed"),
            Genre( "Cooking",  "cooking"),
            Genre( "Dementia",  "dementia"),
            Genre( "Demons",  "demons"),
            Genre( "Doujinshi",  "doujinshi"),
            Genre( "Drama",  "drama"),
            Genre( "Ecchi",  "ecchi"),
            Genre( "Fantasy",  "fantasy"),
            Genre( "Game",  "game"),
            Genre( "Gender Bender",  "gender-bender"),
            Genre( "Harem",  "harem"),
            Genre( "Historical",  "historical"),
            Genre( "Horror",  "horror"),
            Genre( "Isekai",  "isekai"),
            Genre( "Josei",  "josei"),
            Genre( "Kids",  "kids"),
            Genre( "Magic",  "magic"),
            Genre( "Manga",  "manga"),
            Genre( "Manhua",  "manhua"),
            Genre( "Manhwa",  "manhwa"),
            Genre( "Martial Arts",  "martial-arts"),
            Genre( "Mature",  "mature"),
            Genre( "Mecha",  "mecha"),
            Genre( "Military",  "military"),
            Genre( "Music",  "music"),
            Genre( "Mystery",  "mystery"),
            Genre( "Old Comic",  "old-comic"),
            Genre( "One Shot",  "one-shot"),
            Genre( "Oneshot",  "oneshot"),
            Genre( "Parodi",  "parodi"),
            Genre( "Parody",  "parody"),
            Genre( "Police",  "police"),
            Genre( "Psychological",  "psychological"),
            Genre( "Romance",  "romance"),
            Genre( "Samurai",  "samurai"),
            Genre( "School",  "school"),
            Genre( "School Life",  "school-life"),
            Genre( "Sci-Fi",  "sci-fi"),
            Genre( "Seinen",  "seinen"),
            Genre( "Shoujo",  "shoujo"),
            Genre( "Shoujo Ai",  "shoujo-ai"),
            Genre( "Shounen",  "shounen"),
            Genre( "Shounen ai",  "shounen-ai"),
            Genre( "Slice of Life",  "slice-of-life"),
            Genre( "Sports",  "sports"),
            Genre( "Super Power",  "super-power"),
            Genre( "Supernatural",  "supernatural"),
            Genre( "Thriller",  "thriller"),
            Genre( "Tragedy",  "tragedy"),
            Genre( "Vampire",  "vampire"),
            Genre( "Webtoons",  "webtoons"),
            Genre( "Yaoi",  "yaoi"),
            Genre( "Yuri",  "yuri")
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

}