package eu.kanade.tachiyomi.extension.all.mangatensei

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*
import java.util.concurrent.TimeUnit

open class Mangatensei(override val lang: String, private val Mtlang: String) : ParsedHttpSource() {

    override val name = "Mangatensei"

    override val baseUrl = "https://www.mangatensei.com"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun latestUpdatesRequest(page: Int):  Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl = "$baseUrl/browse?langs=$Mtlang&sort=update&page=$page"
        return GET(builtUrl)
    }

    override fun latestUpdatesSelector() = "div#series-list div.col-24"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("a.item-cover")
        val imgurl = "http:" + item.select("img").attr("src")
        manga.setUrlWithoutDomain(item.attr("href"))
        manga.title = element.select("a.item-title").text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "div.browse-pager:contains(order) a.page-link:contains(»)"

    override fun popularMangaRequest(page: Int):  Request {
        val builtUrl = "$baseUrl/browse?langs=$Mtlang&sort=views_w&page=$page"
        return GET(builtUrl)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var author: String? = null
        val url = HttpUrl.parse("$baseUrl/browse")!!.newBuilder()
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("langs", Mtlang)
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                        author = filter.state
                }
                is StyleFilter -> {
                    val styleToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            styleToInclude.add(content.name)
                        }
                    }
                    if (styleToInclude.isNotEmpty()) {
                        url.addQueryParameter("styles", styleToInclude
                            .joinToString(","))
                    }
                }
                is DemographicFilter -> {
                    val demographicToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            demographicToInclude.add(content.name)
                        }
                    }
                    if (demographicToInclude.isNotEmpty()) {
                        url.addQueryParameter("demogs", demographicToInclude
                            .joinToString(","))
                    }
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "1"
                        Filter.TriState.STATE_EXCLUDE -> "0"
                        else -> ""
                    }
                    if(status.isNotEmpty()) {
                        url.addQueryParameter("status", status)
                    }
                }
                is GenreFilter -> {
                    val genreToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            genreToInclude.add(content.name)
                        }
                    }
                    if (genreToInclude.isNotEmpty()) {
                        url.addQueryParameter("genres", genreToInclude
                            .joinToString(","))
                    }
                }
                is StarFilter -> {
                    if(filter.state != 0) {
                        url.addQueryParameter("stars", filter.toUriPart())
                    }
                }
                is ChapterFilter -> {
                    if(filter.state != 0) {
                        url.addQueryParameter("chapters", filter.toUriPart())
                    }
                }
                is SortBy -> {
                    if(filter.state != 0) {
                        url.addQueryParameter("sort", filter.toUriPart())
                    }
                }
            }
        }
        return if(query.isNotBlank() || author!!.isNotBlank()) {
            GET("$baseUrl/search?q=$query&a=$author")
        } else GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#series-page div.container")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = infoElement.select("div.attr-item:contains(status) span").text()
        infoElement.select("div.attr-item:contains(genres) span").text().split(" / "
            .toRegex()).forEach { element ->
            genres.add(element)
        }
        manga.title = infoElement.select("h3").text()
        manga.author = infoElement.select("div.attr-item:contains(author) a:first-child").text()
        manga.artist = infoElement.select("div.attr-item:contains(author) a:last-child").text()
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.description = infoElement.select("h5:contains(summary) + pre").text()
        manga.thumbnail_url = "http:" + document.select("div.attr-cover img")
            .attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "div.main div.p-2"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlElement = element.select("a.chapt")
        val time = element.select("i.pl-3").text()
            .replace("a ", "1 ")
            .replace("an ", "1 ")
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        if (time != "") {
            chapter.date_upload = parseChapterDate(time)
        }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when {
            "mins" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hours" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "days" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "weeks" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "months" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "years" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            "min" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hour" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "day" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "week" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "month" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "year" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val script = document.select("script").html()
            .substringAfter("var images = ").substringBefore(";")
        val imgList = JSONObject(script)

        for( i in 1 until imgList.length() + 1) {
            pages.add(Page(i - 1, "", imgList.getString("$i")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    private class AuthorFilter : Filter.Text("Author / Artist")
    private class StyleFilter(genres: List<Tag>) : Filter.Group<Tag>("Styles", genres)
    private class DemographicFilter(genres: List<Tag>) : Filter.Group<Tag>("Demographic", genres)
    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
    private class StatusFilter : Filter.TriState("Completed")

    private class StarFilter : UriPartFilter("Stars", arrayOf(
            Pair("<select>", ""),
            Pair("5 Stars", "5"),
            Pair("4 Stars", "4"),
            Pair("3 Stars", "3"),
            Pair("2 Stars", "2"),
            Pair("1 Stars", "1")
    ))

    private class ChapterFilter : UriPartFilter("Chapters", arrayOf(
            Pair("<select>", ""),
            Pair("1 ~ 9", "1-9"),
            Pair("10 ~ 29", "10-29"),
            Pair("30 ~ 99", "30-99"),
            Pair("100 ~ 199", "100-199"),
            Pair("200+", "200"),
            Pair("100+", "100"),
            Pair("50+", "50"),
            Pair("10+", "10"),
            Pair("1+", "1")
    ))

    private class SortBy : UriPartFilter("Sorts By", arrayOf(
            Pair("<select>", ""),
            Pair("Totally", "views_t"),
            Pair("365 days", "views_y"),
            Pair("30 days", "views_m"),
            Pair("7 days", "views_w"),
            Pair("24 hours", "views_d"),
            Pair("60 minutes", "views_h"),
            Pair("A-Z", "title"),
            Pair("Update time", "update"),
            Pair("Add time", "create")
    ))

    override fun getFilterList() = FilterList(
            Filter.Header("NOTE: Ignored if using text search!"),
            AuthorFilter(),
            Filter.Separator(),
            StatusFilter(),
            StarFilter(),
            ChapterFilter(),
            SortBy(),
            StyleFilter(getStyleList()),
            DemographicFilter(getDemographicList()),
            GenreFilter(getGenreList())
    )

    private fun getStyleList() = listOf(
            Tag("manga"),
            Tag("manhwa"),
            Tag("manhua"),
            Tag("webtoon")
    )

    private fun getDemographicList() = listOf(
            Tag("josei"),
            Tag("seinen"),
            Tag("shoujo"),
            Tag("shoujo ai"),
            Tag("shounen"),
            Tag("shounen ai"),
            Tag("yaoi"),
            Tag("yuri")
    )

    private fun getGenreList() = listOf(
            Tag("action"),
            Tag("adventure"),
            Tag("award winning"),
            Tag("comedy"),
            Tag("cooking"),
            Tag("demons"),
            Tag("doujinshi"),
            Tag("drama"),
            Tag("ecchi"),
            Tag("fantasy"),
            Tag("gender bender"),
            Tag("harem"),
            Tag("historical"),
            Tag("horror"),
            Tag("isekai"),
            Tag("magic"),
            Tag("martial arts"),
            Tag("mature"),
            Tag("mecha"),
            Tag("medical"),
            Tag("military"),
            Tag("music"),
            Tag("mystery"),
            Tag("one shot"),
            Tag("psychological"),
            Tag("reverse harem"),
            Tag("romance"),
            Tag("school life"),
            Tag("sci fi"),
            Tag("shotacon"),
            Tag("slice of life"),
            Tag("smut"),
            Tag("sports"),
            Tag("super power"),
            Tag("supernatural"),
            Tag("tragedy"),
            Tag("uncategorized"),
            Tag("vampire"),
            Tag("youkai")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(name: String) : Filter.CheckBox(name)

}
