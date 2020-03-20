package eu.kanade.tachiyomi.extension.en.tapastic

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Tapastic : ParsedHttpSource() {

    //Info
    override val lang = "en"
    override val supportsLatest = true
    override val name = "Tapastic"
    override val baseUrl = "https://tapas.io"

    //Popular

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/comics?b=POPULAR&g=&f=NONE&pageNumber=$page&pageSize=20&")

    override fun popularMangaNextPageSelector() = "div[data-has-next=true]"
    override fun popularMangaSelector() = "li.js-list-item"
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        url = element.select(".item__thumb a").attr("href")
        title = element.select(".item__thumb img").attr("alt")
        thumbnail_url = element.select(".item__thumb img").attr("src")
    }

    //Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/comics?b=FRESH&g=&f=NONE&pageNumber=$page&pageSize=20&")

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    //Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //If there is any search text, use text search, otherwise use filter search
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search")
                .buildUpon()
                .appendQueryParameter("t", "COMICS")
                .appendQueryParameter("q", query)
        } else {
            val uri = Uri.parse("$baseUrl/comics").buildUpon()
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri
        }
        //Append page number
        uri.appendQueryParameter("pageNumber", page.toString())
        return GET(uri.toString())
    }

    override fun searchMangaNextPageSelector() =
        "${popularMangaNextPageSelector()}, a.paging__button--next"

    override fun searchMangaSelector() = "${popularMangaSelector()}, .search-item-wrap"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.select(".item__thumb a, .title-section .title a").attr("href")
        val browseTitle = element.select(".item__thumb img")
        title = if (browseTitle != null) {
            browseTitle.attr("alt")
        } else {
            element.select(".title-section .title a").text()
        }
        thumbnail_url = element.select(".item__thumb img, .thumb-wrap img").attr("src")
    }

    //Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(".desc__title").text().trim()
        author = document.select(".tag__author").text().trim()
        artist = author
        description = document.select(".js-series-description").text().trim()
        genre = document.select("div.info__genre a, div.item__genre a")
            .joinToString(", ") { it.text() }
    }

    //Chapters

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + "?sort_order=desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val baseUri = document.baseUri().substringBefore("?")
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        var nextPage = document.select(".paging__button--next:not(.disabled)")
        while (!nextPage.isNullOrEmpty()) {
            document = client.newCall(GET(baseUri + nextPage.attr("href"))).execute().asJsoup()
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            nextPage = document.select(".paging__button--next:not(.disabled)")
        }
        return chapters
    }

    override fun chapterListSelector() = "li.content__item"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val lock = !element.select(".sp-ico-episode-lock, .sp-ico-schedule-white").isNullOrEmpty()
        name = if (lock) {
            "\uD83D\uDD12 "
        } else {
            ""
        } + element.select(".info__title").text().trim()

        url = if (lock) {
            "locked"
        } else {
            element.select("a").first().attr("href")
        }
        chapter_number =
            element.select(".info__header").text().substringAfter("Episode")
                .substringBefore("Early access").trim().toFloat()

        date_upload =
            parseDate(element.select(".info__tag").text().substringAfter(":").substringBefore("•").trim())
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(date)?.time ?: 0
    }

    //Pages

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url == "locked") throw Exception("Chapter Locked. If logged in, refresh chapter list.")
        return GET(baseUrl + chapter.url, headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("img.content__img").forEach {
            add(Page(size, "", if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src")))
        }
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")

    //Filters

    override fun getFilterList() = FilterList(
        //Tapastic does not support genre filtering and text search at the same time
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        FilterFilter(),
        GenreFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Sort is ignored when filter is active!"),
        SortFilter()
    )

    private class FilterFilter : UriSelectFilter(
        "Filter", "b", arrayOf(
            Pair("ALL", "None"),
            Pair("POPULAR", "Popular"),
            Pair("TRENDING", "Trending"),
            Pair("FRESH", "Fresh"),
            Pair("BINGE", "Binge"),
            Pair("ORIGINAL", "Tapas Originals")
        ), firstIsUnspecified = false, defaultValue = 1
    )

    private class GenreFilter : UriSelectFilter(
        "Genre", "g", arrayOf(
            Pair("", "Any"),
            Pair("7", "Action"),
            Pair("22", "Boys Love"),
            Pair("2", "Comedy"),
            Pair("8", "Drama"),
            Pair("3", "Fantasy"),
            Pair("24", "Girls Love"),
            Pair("9", "Gaming"),
            Pair("6", "Horror"),
            Pair("25", "LGBTQ+"),
            Pair("10", "Mystery"),
            Pair("5", "Romance"),
            Pair("4", "Science Fiction"),
            Pair("1", "Slice of Life")
        )
    )

    private class StatusFilter : UriSelectFilter(
        "Status", "f", arrayOf(
            Pair("NONE", "All"),
            Pair("F2R", "Free to read"),
            Pair("PRM", "Premium")
        )
    )

    private class SortFilter : UriSelectFilter(
        "Sort", "s", arrayOf(
            Pair("DATE", "Date"),
            Pair("LIKE", "Likes"),
            Pair("SUBSCRIBE", "Subscribers")
        )
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilter(
        displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue),
        UriFilter {
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
