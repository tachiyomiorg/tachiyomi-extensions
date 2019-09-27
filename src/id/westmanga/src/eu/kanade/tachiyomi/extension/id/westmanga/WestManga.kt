package eu.kanade.tachiyomi.extension.id.westmanga

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class WestManga : ParsedHttpSource() {

    override val name = "West Manga"
    override val baseUrl = "https://westmanga.info"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/manga-list/?popular" else "$baseUrl/manga-list/page/$page/?popular"
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/manga-list/?latest" else "$baseUrl/manga-list/page/$page/?latest"
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var builtUrl = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        if (!query.equals(null) && !query.equals("")) {
            builtUrl = if (page == 1) "$baseUrl/?s=$query&post_type=manga" else "$baseUrl/page/2/?s=$query&post_type=manga"
        } else if (filters.size > 0) {
            filters.forEach { filter ->
                when (filter) {
                    is SortByFilter -> {
                        builtUrl = if (page == 1) "$baseUrl/manga-list/?" + filter.toUriPart() else "$baseUrl/manga-list/page/$page/?" + filter.toUriPart()
                    }
                    is GenreListFilter -> {
                        builtUrl = if (page == 1) "$baseUrl/genre/" + filter.toUriPart() else "$baseUrl/genre/" + filter.toUriPart() + "/page/$page/"
                    }
                }
            }
        }
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "div.result-search"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.fletch > .img_search > img").attr("src")
        element.select(".kanan_search > .search_title > .titlex > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".paginado>ul>li.dd + li.a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("table.attr").first()
        val descElement = document.select("div.sin").first()
        val sepName = infoElement.select("tr:nth-child(5)>td").first()
        val manga = SManga.create()
        manga.author = sepName.text()
        manga.artist = sepName.text()
        val genres = mutableListOf<String>()
        infoElement.select("tr:nth-child(6)>td > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("tr:nth-child(4)>td").text())
        manga.description = descElement.select("p").text()
        manga.thumbnail_url = document.select(".topinfo > img").attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("publishing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.cl ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".leftoff > a").first()
        val chapter = SChapter.create()
        val timeElement = element.select("span.rightoff").first()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        val sdf = SimpleDateFormat("MMM dd, yyyy")
        val parse = sdf.parse(date)
        val cal = Calendar.getInstance()
        cal.time = parse
        return cal.timeInMillis
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
        document.select("div#readerarea img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.length != 0) {
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

    private class SortByFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Default", ""),
        Pair("A-Z", "A-Z"),
        Pair("Latest Added", "latest"),
        Pair("Popular", "popular")
    ))

    private class GenreListFilter : UriPartFilter("Genre", arrayOf(
        Pair("Default", ""),
        Pair("4-Koma", "4-koma"),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("FantasyAction", "fantasyaction"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Gore", "gore"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horro", "horro"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Isekai Action", "isekai-action"),
        Pair("Josei", "josei"),
        Pair("Magic", "magic"),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Martial arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Oneshot", "oneshot"),
        Pair("Project", "project"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School", "school"),
        Pair("School life", "school-life"),
        Pair("Sci fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Suspense", "suspense"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Vampire", "vampire"),
        Pair("Webtoons", "webtoons"),
        Pair("Yuri", "yuri")
    ))

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: sort and genre can't be combined and ignored when using text search!"),
        Filter.Separator(),
        SortByFilter(),
        GenreListFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
