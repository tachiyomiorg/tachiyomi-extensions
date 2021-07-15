package eu.kanade.tachiyomi.extension.en.mangaowl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaOwl : ParsedHttpSource() {

    override val name = "MangaOwl"

    override val baseUrl = "https://mangaowls.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/$page", headers)
    }

    override fun popularMangaSelector() = "div.col-md-2"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h6 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("div.img-responsive").attr("abs:data-background-image")

        return manga
    }

    override fun popularMangaNextPageSelector() = "div.blog-pagenat-wthree li a:contains(>>)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/lastest/$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/$page?search=$query".toHttpUrlOrNull()!!.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SearchFilter -> url.addQueryParameter("search_field", filter.toUriPart())
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is StatusFilter -> url.addQueryParameter("completed", filter.toUriPart())
                is GenreFilter -> {
                        val genres = filter.state
                            .filter { it.state }
                            .joinToString(".") { it.uriPart }
                        url.addQueryParameter("genres", genres)
                }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "div.navigation li a:contains(next)"

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.single_detail").first()

        return SManga.create().apply {
            title = infoElement.select("h2").first().ownText()
            author = infoElement.select("p.fexi_header_para a.author_link").text()
            artist = author
            status = parseStatus(infoElement.select("p.fexi_header_para:contains(status)").first().ownText())
            genre = infoElement.select("div.col-xs-12.col-md-8.single-right-grid-right > p > a[href*=genres]").joinToString { it.text() }
            description = infoElement.select(".description").first().ownText()
            thumbnail_url = infoElement.select("img").first()?.let { img ->
                if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
            }
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.table-chapter-list ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("a").let {
            // They replace some URLs with a different host getting a path of domain.com/reader/reader/, fix to make usable on baseUrl
            chapter.setUrlWithoutDomain(it.attr("href").replace("/reader/reader/", "/reader/"))
            chapter.name = it.select("label")[0].text()
        }
        chapter.date_upload = parseChapterDate(element.select("small:last-of-type").text())

        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MM/dd/yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long {
        return try {
            dateFormat.parse(string)?.time ?: 0
        } catch (_: ParseException) {
            0
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.item img.owl-lazy").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")


    // Filters

    override fun getFilterList() = FilterList(
        SearchFilter(),
        SortFilter(),
        StatusFilter(),
        GenreFilter(getGenreList())
    )
    
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
    
    private class SearchFilter : UriPartFilter(
        "Search in",
        arrayOf(
            Pair("All", "123"),
            Pair("Manga title", "1"),
            Pair("Authors", "2"),
            Pair("Description", "3")
        )
    )

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Matched", "4"),
            Pair("Viewed", "0"),
            Pair("Popularity", "1"),
            Pair("Create Date", "2"),
            Pair("Upload Date", "3")
        )
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Any", "2"),
            Pair("Completed", "1"),
            Pair("Ongoing", "0")
        )
    )

    private class Genre(name: String, val uriPart: String) : Filter.CheckBox(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private fun getGenreList() = listOf(
        Genre("4-koma", "89"),
        Genre("Action", "1"),
        Genre("Adaptation", "72"),
        Genre("Adventure", "2"),
        Genre("Aliens", "112"),
        Genre("All Ages", "122"),
        Genre("Animals", "90"),
        Genre("Anthology", "101"),
        Genre("Award winning", "91"),
        Genre("Bara", "116"),
        Genre("Cars", "49"),
        Genre("Comedy", "15"),
        Genre("Comic", "130"),
        Genre("Cooking", "63"),
        Genre("Crime", "81"),
        Genre("Crossdressing", "105"),
        Genre("Delinquents", "73"),
        Genre("Dementia", "48"),
        Genre("Demons", "3"),
        Genre("Doujinshi", "55"),
        Genre("Drama", "4"),
        Genre("Ecchi", "27"),
        Genre("Fan colored", "92"),
        Genre("Fantasy", "7"),
        Genre("Full Color", "82"),
        Genre("Game", "33"),
        Genre("Gender Bender", "39"),
        Genre("Ghosts", "97"),
        Genre("Gore", "107"),
        Genre("Gossip", "123"),
        Genre("Gyaru", "104"),
        Genre("Harem", "38"),
        Genre("Historical", "12"),
        Genre("Horror", "5"),
        Genre("Incest", "98"),
        Genre("Isekai", "69"),
        Genre("Japanese", "129"),
        Genre("Josei", "35"),
        Genre("Kids", "42"),
        Genre("Korean", "128"),
        Genre("Long Strip", "76"),
        Genre("Mafia", "82"),
        Genre("Magic", "34"),
        Genre("Magical Girls", "88"),
        Genre("Manga", "127"),
        Genre("Manhua", "62"),
        Genre("Manhwa", "61"),
        Genre("Martial Arts", "37"),
        Genre("Mature", "60"),
        Genre("Mecha", "36"),
        Genre("Medical", "66"),
        Genre("Military", "8"),
        Genre("Monster girls", "95"),
        Genre("Monsters", "84"),
        Genre("Music", "32"),
        Genre("Mystery", "11"),
        Genre("Ninja", "93"),
        Genre("Novel", "56"),
        Genre("NTR", "121"),
        Genre("Office", "126"),
        Genre("Office Workers", "99"),
        Genre("Official colored", "78"),
        Genre("One shot", "67"),
        Genre("Parody", "30"),
        Genre("Philosophical", "100"),
        Genre("Police", "46"),
        Genre("Post apocalyptic", "94"),
        Genre("Psychological", "9"),
        Genre("Reincarnation", "74"),
        Genre("Reverse harem", "79"),
        Genre("Romance", "25"),
        Genre("Samurai", "18"),
        Genre("School life", "59"),
        Genre("Sci-fi", "70"),
        Genre("Seinen", "10"),
        Genre("Sexual violence", "117"),
        Genre("Shoujo", "28"),
        Genre("Shoujo Ai", "40"),
        Genre("Shounen", "13"),
        Genre("Shounen Ai", "44"),
        Genre("Slice of Life", "19"),
        Genre("Smut", "65"),
        Genre("Space", "29"),
        Genre("Sports", "22"),
        Genre("Super Power", "17"),
        Genre("Superhero", "109"),
        Genre("Supernatural", "6"),
        Genre("Survival", "85"),
        Genre("Thriller", "31"),
        Genre("Time travel", "80"),
        Genre("Toomics", "120"),
        Genre("Traditional games", "113"),
        Genre("Tragedy", "68"),
        Genre("Uncategorized", "50"),
        Genre("Uncensored", "124"),
        Genre("User created", "102"),
        Genre("Vampires", "103"),
        Genre("Vanilla", "125"),
        Genre("Video games", "75"),
        Genre("Villainess", "119"),
        Genre("Virtual reality", "110"),
        Genre("Web comic", "77"),
        Genre("Webtoon", "71"),
        Genre("Wuxia", "106"),
        Genre("Yaoi", "51"),
        Genre("Yuri", "54"),
        Genre("Zombies", "108")
    )
}
