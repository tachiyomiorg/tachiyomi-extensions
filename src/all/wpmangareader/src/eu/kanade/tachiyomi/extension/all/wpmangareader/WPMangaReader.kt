package eu.kanade.tachiyomi.extension.all.wpmangareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

abstract class WPMangaReader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val mangaUrlDirectory: String = "/manga",
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx "

    override fun popularMangaRequest(page: Int) = GET("$baseUrl$mangaUrlDirectory/?page/$page&order=popular", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").attr("src")
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun popularMangaNextPageSelector() = "div.pagination .next, div.hpage .r"

    // latest
    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl$mangaUrlDirectory/?page/$page&order=update", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filters = if (filters.isEmpty()) getFilterList() else filters
        val genre = filters.findInstance<GenreList>()?.toUriPart()
        val order = filters.findInstance<OrderByFilter>()?.toUriPart()

        return when {
            order!!.isNotEmpty() -> GET("$baseUrl$mangaUrlDirectory/?page/$page&order=$order")
            genre!!.isNotEmpty() -> GET("$baseUrl/genres/$genre/page/$page/?s=$query")
            else -> GET("$baseUrl/page/$page/?s=$query")
        }
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".listinfo li:contains(Author), .tsinfo .imptdt:nth-child(4) i, .infotable tr:contains(author) td:last-child")
            .firstOrNull()?.ownText()

        artist = document.select(".infotable tr:contains(artist) td:last-child, .tsinfo .imptdt:contains(artist) i")
            .firstOrNull()?.ownText()

        genre = document.select("div.gnr a, .mgen a, .seriestugenre a").joinToString { it.text() }
        status = parseStatus(
            document.select("div.listinfo li:contains(Status), .tsinfo .imptdt:contains(status), .infotable tr:contains(status) td")
                .text()
        )

        thumbnail_url = document.select(".infomanga > div[itemprop=image] img, .thumb img").attr("src")
        description = document.select(".desc, .entry-content[itemprop=description]").joinToString("\n") { it.text() }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "div.bxcl li, #chapterlist li .eph-num a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        // Add timestamp to latest chapter, taken from "Updated On". so source which not provide chapter timestamp will have atleast one
        val date = document.select(".listinfo time[itemprop=dateModified]").attr("datetime")
        val checkChapter = document.select(chapterListSelector()).firstOrNull()
        if (date != "" && checkChapter != null) chapters[0].date_upload = parseDate(date)

        return chapters
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(date)?.time ?: 0L
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href").substringAfter(baseUrl))
        name = element.select(".lch a, .chapternum").text()
        date_upload = element.select(".chapterdate").firstOrNull()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    // pages
    open val pageSelector = "div#readerarea img"

    override fun pageListParse(document: Document): List<Page> {
        var pages = mutableListOf<Page>()
        document.select(pageSelector)
            .filterNot { it.attr("src").isNullOrEmpty() }
            .mapIndexed { i, img -> pages.add(Page(i, "", img.attr("abs:src"))) }

        // Some sites like mangakita now load pages via javascript
        if (pages.isNotEmpty()) { return pages }

        val docString = document.toString()
        val imageListRegex = Regex("\\\"images.*?:.*?(\\[.*?\\])")

        val imageList = JSONArray(imageListRegex.find(docString)!!.destructured.toList()[0])

        for (i in 0 until imageList.length()) {
            pages.add(Page(i, "", imageList.getString(i)))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    // filters
    override fun getFilterList() = FilterList(
        Filter.Header("Order by filter cannot be used with others"),
        OrderByFilter(),
        Filter.Separator(),
        GenreList()
    )

    private class OrderByFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("", "<select>"),
            Pair("title", "A-Z"),
            Pair("update", "Latest Update"),
            Pair("create", "Latest Added")
        )
    )

    private class GenreList : UriPartFilter(
        "Select Genre",
        arrayOf(
            Pair("", "<select>"),
            Pair("4-koma", "4-Koma"),
            Pair("action", "Action"),
            Pair("adaptation", "Adaptation"),
            Pair("adult", "Adult"),
            Pair("adventure", "Adventure"),
            Pair("animal", "Animal"),
            Pair("animals", "Animals"),
            Pair("anthology", "Anthology"),
            Pair("apocalypto", "Apocalypto"),
            Pair("comedy", "Comedy"),
            Pair("comic", "Comic"),
            Pair("cooking", "Cooking"),
            Pair("crime", "Crime"),
            Pair("demons", "Demons"),
            Pair("doujinshi", "Doujinshi"),
            Pair("drama", "Drama"),
            Pair("ecchi", "Ecchi"),
            Pair("fantasi", "Fantasi"),
            Pair("fantasy", "Fantasy"),
            Pair("game", "Game"),
            Pair("gender-bender", "Gender Bender"),
            Pair("genderswap", "Genderswap"),
            Pair("drama", "Drama"),
            Pair("gore", "Gore"),
            Pair("harem", "Harem"),
            Pair("hentai", "Hentai"),
            Pair("historical", "Historical"),
            Pair("horor", "Horor"),
            Pair("horror", "Horror"),
            Pair("isekai", "Isekai"),
            Pair("josei", "Josei"),
            Pair("kingdom", "kingdom"),
            Pair("magic", "Magic"),
            Pair("manga", "Manga"),
            Pair("manhua", "Manhua"),
            Pair("manhwa", "Manhwa"),
            Pair("martial-art", "Martial Art"),
            Pair("martial-arts", "Martial Arts"),
            Pair("mature", "Mature"),
            Pair("mecha", "Mecha"),
            Pair("medical", "Medical"),
            Pair("military", "Military"),
            Pair("modern", "Modern"),
            Pair("monster", "Monster"),
            Pair("monster-girls", "Monster Girls"),
            Pair("music", "Music"),
            Pair("mystery", "Mystery"),
            Pair("oneshot", "Oneshot"),
            Pair("post-apocalyptic", "Post-Apocalyptic"),
            Pair("project", "Project"),
            Pair("psychological", "Psychological"),
            Pair("reincarnation", "Reincarnation"),
            Pair("romance", "Romance"),
            Pair("romancem", "Romancem"),
            Pair("samurai", "Samurai"),
            Pair("school", "School"),
            Pair("school-life", "School Life"),
            Pair("sci-fi", "Sci-Fi"),
            Pair("seinen", "Seinen"),
            Pair("shoujo", "Shoujo"),
            Pair("shoujo-ai", "Shoujo Ai"),
            Pair("shounen", "Shounen"),
            Pair("shounen-ai", "Shounen Ai"),
            Pair("slice-of-life", "Slice of Life"),
            Pair("smut", "Smut"),
            Pair("sports", "Sports"),
            Pair("style-ancient", "Style ancient"),
            Pair("super-power", "Super Power"),
            Pair("superhero", "Superhero"),
            Pair("supernatural", "Supernatural"),
            Pair("survival", "Survival"),
            Pair("survive", "Survive"),
            Pair("thriller", "Thriller"),
            Pair("time-travel", "Time Travel"),
            Pair("tragedy", "Tragedy"),
            Pair("urban", "Urban"),
            Pair("vampire", "Vampire"),
            Pair("video-games", "Video Games"),
            Pair("virtual-reality", "Virtual Reality"),
            Pair("webtoons", "Webtoons"),
            Pair("yuri", "Yuri"),
            Pair("zombies", "Zombies")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
