package eu.kanade.tachiyomi.extension.en.mangaowl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
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

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/popular/$page", headers)

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

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/lastest/$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = 
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/$page".toHttpUrlOrNull()?.newBuilder()!!
        url.addQueryParameter("search", query)

        filters.forEach { filter ->
            when (filter!!) {
                is SearchFilter -> url.addQueryParameter("search_field", filter.toUriPart())
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is StatusFilter -> url.addQueryParameter("completed", filter.toUriPart())
                is GenreFilter -> url.addQueryParameter("genres", filter.toUripart())
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() =
        popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "div.navigation li a:contains(next)"

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

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.item img.owl-lazy").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

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
        "Search",
        arrayOf(
            Pair("All", "123"),
            Pair("Manga title", "1"),
            Pair("Authors", "2"),
            Pair("Description", "3")
        )
    )

    private class SortFilter : UriPartFilter(
        "Sort",
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

    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("4-koma", "89"),
            Pair("Action", "1"),
            Pair("Adaptation", "72"),
            Pair("Adventure", "2"),
            Pair("Aliens", "112"),
            Pair("All Ages", "122"),
            Pair("Animals", "90"),
            Pair("Anthology", "101"),
            Pair("Award winning", "91"),
            Pair("Bara", "116"),
            Pair("Cars", "49"),
            Pair("Comedy", "15"),
            Pair("Comic", "130"),
            Pair("Cooking", "63"),
            Pair("Crime", "81"),
            Pair("Crossdressing", "105"),
            Pair("Delinquents", "73"),
            Pair("Dementia", "48"),
            Pair("Demons", "3"),
            Pair("Doujinshi", "55"),
            Pair("Drama", "4"),
            Pair("Ecchi", "27"),
            Pair("Fan colored", "92"),
            Pair("Fantasy", "7"),
            Pair("Full Color", "82"),
            Pair("Game", "33"),
            Pair("Gender Bender", "39"),
            Pair("Ghosts", "97"),
            Pair("Gore", "107"),
            Pair("Gossip", "123"),
            Pair("Gyaru", "104"),
            Pair("Harem", "38"),
            Pair("Historical", "12"),
            Pair("Horror", "5"),
            Pair("Incest", "98"),
            Pair("Isekai", "69"),
            Pair("Japanese", "129"),
            Pair("Josei", "35"),
            Pair("Kids", "42"),
            Pair("Korean", "128"),
            Pair("Long Strip", "76"),
            Pair("Mafia", "82"),
            Pair("Magic", "34"),
            Pair("Magical Girls", "88"),
            Pair("Manga", "127"),
            Pair("Manhua", "62"),
            Pair("Manhwa", "61"),
            Pair("Martial Arts", "37"),
            Pair("Mature", "60"),
            Pair("Mecha", "36"),
            Pair("Medical", "66"),
            Pair("Military", "8"),
            Pair("Monster girls", "95"),
            Pair("Monsters", "84"),
            Pair("Music", "32"),
            Pair("Mystery", "11"),
            Pair("Ninja", "93"),
            Pair("Novel", "56"),
            Pair("NTR", "121"),
            Pair("Office", "126"),
            Pair("Office Workers", "99"),
            Pair("Official colored", "78"),
            Pair("One shot", "67"),
            Pair("Parody", "30"),
            Pair("Philosophical", "100"),
            Pair("Police", "46"),
            Pair("Post apocalyptic", "94"),
            Pair("Psychological", "9"),
            Pair("Reincarnation", "74"),
            Pair("Reverse harem", "79"),
            Pair("Romance", "25"),
            Pair("Samurai", "18"),
            Pair("School life", "59"),
            Pair("Sci-fi", "70"),
            Pair("Seinen", "10"),
            Pair("Sexual violence", "117"),
            Pair("Shoujo", "28"),
            Pair("Shoujo Ai", "40"),
            Pair("Shounen", "13"),
            Pair("Shounen Ai", "44"),
            Pair("Slice of Life", "19"),
            Pair("Smut", "65"),
            Pair("Space", "29"),
            Pair("Sports", "22"),
            Pair("Super Power", "17"),
            Pair("Superhero", "109"),
            Pair("Supernatural", "6"),
            Pair("Survival", "85"),
            Pair("Thriller", "31"),
            Pair("Time travel", "80"),
            Pair("Toomics", "120"),
            Pair("Traditional games", "113"),
            Pair("Tragedy", "68"),
            Pair("Uncategorized", "50"),
            Pair("Uncensored", "124"),
            Pair("User created", "102"),
            Pair("Vampires", "103"),
            Pair("Vanilla", "125"),
            Pair("Video games", "75"),
            Pair("Villainess", "119"),
            Pair("Virtual reality", "110"),
            Pair("Web comic", "77"),
            Pair("Webtoon", "71"),
            Pair("Wuxia", "106"),
            Pair("Yaoi", "51"),
            Pair("Yuri", "54"),
            Pair("Zombies", "108")
        )
    )
}
