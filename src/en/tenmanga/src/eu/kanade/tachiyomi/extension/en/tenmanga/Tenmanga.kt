package eu.kanade.tachiyomi.extension.en.tenmanga

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class Tenmanga : HttpSource() {

    override val lang = "en"
    override val name = "Tenmanga"
    override val baseUrl = "http://www.tenmanga.com"
    override val supportsLatest = true

    private val ajaxUrl = "/ajax"
    private val hotUrl = "$ajaxUrl/hot_manga/page-"
    private val latestUrl = "$ajaxUrl/lastest/page-"
    private val searchURL = "$ajaxUrl/search/"

    // Popular manga
    override fun popularMangaRequest(page: Int) = GET(baseUrl + hotUrl + page, headers)

    override fun popularMangaParse(response: Response): MangasPage = ajaxResponseToMangasPage(response)

    // Latest manga
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl + latestUrl + page, headers)

    override fun latestUpdatesParse(response: Response): MangasPage = ajaxResponseToMangasPage(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl + searchURL).buildUpon()
            .appendQueryParameter("page", page.toString())

        filters.forEach {
            if (it is UriFilter)
                it.addToUri(uri)
        }

        if (query.isNotEmpty()) {
            uri.appendQueryParameter("wd", query)
            if (filters.isEmpty())
                uri.appendQueryParameter("name_sel", "contain")
        }

        return GET(uri.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = ajaxResponseToMangasPage(response)

    // Commonly used methods for latest and hot manga, as well as for search responses
    private val mangaSelector = "li > dl > dt > a"

    /**
     * The extension performs AJAX Get requests whenever it's looking for a list of mangas.
     * Here the extension transforms the returned website response into a Tachiyomi-usable MangasPage.
     */
    private fun ajaxResponseToMangasPage(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()

        val document = response.asJsoup()
        document.select(mangaSelector).forEach { e -> if (e != null) mangas.add(mangaFromElement(e)) }

        return MangasPage(mangas, mangas.size == 40)
    }

    /**
     * Individual manga entries of the AJAX response are parsed into Tachiyomi-usable Manga.
     */
    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = element.select("img").firstOrNull()?.attr("src")
    }

    // MangaDetails
    override fun mangaDetailsParse(response: Response): SManga {
        val bookInfo = response.asJsoup().select("div.book-info").firstOrNull()

        return SManga.create().also {
            if (bookInfo != null)
                it.apply {
                    setUrlWithoutDomain(response.request().url().toString())
                    title = bookInfo.select("h1 > b").first().text()
                    description = bookInfo.select("p:has(b:contains(Manga Summary)) > span").first().text()
                    author = bookInfo.select("p:has(span:contains(Author(s))) > a").first().text()
                    thumbnail_url = bookInfo.select("img").attr("src")
                    genre = bookInfo.select("ul.inset-menu > li > a").filter {
                        e -> e.ownText() != "Manga Reviews"
                    }.joinToString(", ") { e -> e.ownText() }
                    status = when (bookInfo.select("p:has(span:contains(Status:)) > a").first().text()) {
                        "Ongoing" -> SManga.ONGOING
                        "Completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
        }
    }

    // Chapters
    /**
     * The website protects viewers from "intense violence, blood/gore,sexual content
     * and/or strong language that may not be appropriate for underage viewers" with a disclaimer.
     * Clicking on a link in the disclaimer adds a waring=1 to the URL parameters and reloads the website, removing the disclaimer.
     * Sending waring=1 with all chapter requests allows the extension to never run into this disclaimer.
     */
    override fun chapterListRequest(manga: SManga): Request =
        GET(Uri.parse(baseUrl + manga.url).buildUpon().appendQueryParameter("waring", "1").build().toString(), headers)

    private val chapterListSelector: String = "ul.chapter-box > li:not(li.list-name)"

    override fun chapterListParse(response: Response): List<SChapter> =
        response.asJsoup().select(chapterListSelector).map { e -> chapterFromElement(e) }

    private val chapterElementSelector: String = "div.chapter-name.long > a"
    private fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            val chapterNameElement = element.select(chapterElementSelector).first()
            setUrlWithoutDomain(chapterNameElement.attr("href"))
            name = chapterNameElement.ownText()
            date_upload = parseDate(element.select("div.add-time > span").first().ownText())
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseDate(dateAsString: String): Long {
        return dateFormat.parse(dateAsString)?.time ?: 0
    }

    // Chapter
    override fun pageListParse(response: Response): List<Page> =
        response.asJsoup().select("select.sl-page > option")
        .mapIndexed { index, element -> Page(index, element.attr("value")) }

    override fun imageUrlParse(response: Response): String =
        response.asJsoup().select("img.manga_pic").first().attr("src")

    // Filter
    override fun getFilterList() = FilterList(
        SearchTypeFilter("Series Name mode", "name_sel"),
        GenreGroup(),
        AuthorText(),
        ArtistText(),
        StatusFilter()
    )

    private class AuthorText : Filter.Text("Author Name"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state.isNotEmpty()) {
                uri.appendQueryParameter("author", state)
                uri.appendQueryParameter("author_sel", "contain")
            }
        }
    }

    private class ArtistText : Filter.Text("Artist Name"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state.isNotEmpty()) {
                uri.appendQueryParameter("artist", state)
                uri.appendQueryParameter("artist_sel", "contain")
            }
        }
    }

    private class SearchTypeFilter(name: String, uriParam: String) :
        UriSelectFilter(name, uriParam, arrayOf(
            Pair("contain", "contains term"),
            Pair("begin", "begins with term"),
            Pair("end", "ends with term")
        ), false)

    private class GenreFilter(val uriParam: String, displayName: String) : Filter.TriState(displayName)

    private class GenreGroup : Filter.Group<GenreFilter>("Genres", listOf(
        GenreFilter("56", "4-Koma"),
        GenreFilter("1", "Action"),
        GenreFilter("39", "Adult"),
        GenreFilter("2", "Adventure"),
        GenreFilter("3", "Anime"),
        GenreFilter("59", "Award Winning"),
        GenreFilter("4", "Comedy"),
        GenreFilter("5", "Cooking"),
        GenreFilter("49", "Demons"),
        GenreFilter("45", "Doujinshi"),
        GenreFilter("6", "Drama"),
        GenreFilter("7", "Ecchi"),
        GenreFilter("8", "Fantasy"),
        GenreFilter("9", "Gender Bender"),
        GenreFilter("10", "Harem"),
        GenreFilter("11", "Historical"),
        GenreFilter("12", "Horror"),
        GenreFilter("13", "Josei"),
        GenreFilter("14", "Live Action"),
        GenreFilter("47", "Magic"),
        GenreFilter("15", "Manhua"),
        GenreFilter("16", "Manhwa"),
        GenreFilter("17", "Martial Arts"),
        GenreFilter("37", "Matsumoto Tomokicomedy"),
        GenreFilter("36", "Mature"),
        GenreFilter("18", "Mecha"),
        GenreFilter("19", "Medical"),
        GenreFilter("51", "Military"),
        GenreFilter("20", "Music"),
        GenreFilter("21", "Mystery"),
        GenreFilter("54", "N/A"),
        GenreFilter("22", "One Shot"),
        GenreFilter("57", "Oneshot"),
        GenreFilter("23", "Psychological"),
        GenreFilter("55", "Reverse Harem"),
        GenreFilter("24", "Romance"),
        GenreFilter("38", "Romance Shoujo"),
        GenreFilter("25", "School Life"),
        GenreFilter("26", "Sci-Fi"),
        GenreFilter("27", "Seinen"),
        GenreFilter("28", "Shoujo"),
        GenreFilter("44", "Shoujo Ai"),
        GenreFilter("29", "Shoujo-Ai"),
        GenreFilter("48", "Shoujoai"),
        GenreFilter("30", "Shounen"),
        GenreFilter("42", "Shounen Ai"),
        GenreFilter("31", "Shounen-Ai"),
        GenreFilter("46", "Shounenai"),
        GenreFilter("32", "Slice Of Life"),
        GenreFilter("41", "Smut"),
        GenreFilter("33", "Sports"),
        GenreFilter("34", "Supernatural"),
        GenreFilter("53", "Suspense"),
        GenreFilter("35", "Tragedy"),
        GenreFilter("52", "Vampire"),
        GenreFilter("58", "Webtoon"),
        GenreFilter("50", "Webtoons"),
        GenreFilter("40", "Yaoi"),
        GenreFilter("43", "Yuri")
    )), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            val genresParameterValue = state.filter { it.isIncluded() }.joinToString(",") { it.uriParam }
            if (genresParameterValue.isNotEmpty()) {
                uri.appendQueryParameter("category_id", genresParameterValue)
            }

            val genresExcludeParameterValue = state.filter { it.isExcluded() }.joinToString(",") { it.uriParam }
            if (genresExcludeParameterValue.isNotEmpty()) {
                uri.appendQueryParameter("out_category_id", genresExcludeParameterValue)
            }
        }
    }

    private class StatusFilter : Filter.TriState("Completed Series"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("completed_series", when (state) {
                STATE_IGNORE -> "either"
                STATE_EXCLUDE -> "no"
                STATE_INCLUDE -> "yes"
                else -> ""
            })
        }
    }

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}
