package eu.kanade.tachiyomi.extension.id.ngomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Ngomik : ParsedHttpSource() {

    override val name = "Ngomik"

    override val baseUrl = "https://ngomik.in"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.imgu > a.series"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/all-komik/page/$page/?order=popular", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.select("img").attr("src")
    }

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/all-komik/page/$page/?order=update", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filters = if (filters.isEmpty()) getFilterList() else filters
        val genre = filters.findInstance<GenreList>()?.toUriPart()
        val order = filters.findInstance<OrderByFilter>()?.toUriPart()

        return when {
            order!!.isNotEmpty() -> GET("$baseUrl/all-komik/page/$page/?order=$order")
            genre!!.isNotEmpty() -> GET("$baseUrl/genres/$genre/page/$page/?s=$query")
            else -> GET("$baseUrl/page/$page/?s=$query")
        }
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1[itemprop=headline]").text()
        author = document.select("div.listinfo li")[2].text().removePrefix("Author: ")
        description = document.select(".desc").text()
        genre = document.select("div.gnr > a").joinToString { it.text() }
        status = parseStatus(document.select("div.listinfo li")[3].text())
        thumbnail_url = document.select("div[itemprop=image] > img").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.lch > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img").mapIndexed { i, element ->
            Page(i, "", element.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList(
            Filter.Header("Order by filter cannot be used with others"),
            OrderByFilter(),
            Filter.Separator(),
            GenreList()
    )

    private class OrderByFilter : UriPartFilter("Order By", arrayOf(
            Pair("", "<select>"),
            Pair("title", "A-Z"),
            Pair("update", "Latest Update"),
            Pair("create", "Latest Added")
    ))

    private class GenreList : UriPartFilter("Select Genre", arrayOf(
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
    ))

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
