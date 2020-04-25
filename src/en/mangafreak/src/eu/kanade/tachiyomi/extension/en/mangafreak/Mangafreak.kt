package eu.kanade.tachiyomi.extension.en.mangafreak

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mangafreak : ParsedHttpSource() {
    override val name: String = "Mangafreak"
    override val lang: String = "en"
    override val baseUrl: String = "https://w11.mangafreak.net"
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/Genre/All/$page", headers)
    }
    override fun popularMangaNextPageSelector(): String? = "a.next_p"
    override fun popularMangaSelector(): String = "div.ranking_item"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select("a").apply {
            title = text()
            url = attr("href")
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/Latest_Releases/$page", headers)
    }
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = "div.latest_releases_item"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src").replace("mini", "manga").substringBeforeLast("/") + ".jpg"
        element.select("a").apply {
            title = text()
            url = attr("href")
        }
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        if (!query.isBlank()) {
            uri.appendPath("Search")
                .appendPath(query)
        }
        filters.forEach { filter ->
            uri.appendPath("Genre")
            when (filter) {
                is GenreList -> {
                    uri.appendPath(filter.state.map {
                        when (it.state) {
                            Filter.TriState.STATE_IGNORE -> "0"
                            Filter.TriState.STATE_INCLUDE -> "1"
                            Filter.TriState.STATE_EXCLUDE -> "2"
                            else -> "0"
                        }
                    }.joinToString(""))
                }
            }
            uri.appendEncodedPath("Status/0/Type/0")
        }
        return GET(uri.toString(), headers)
    }
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector(): String = "div.manga_search_item , div.mangaka_search_item"
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.manga_series_image img").attr("abs:src")
        title = document.select("div.manga_series_data h5").text()
        status = when (document.select("div.manga_series_data > div:eq(3)").text()) {
            "ON-GOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = document.select("div.manga_series_data > div:eq(4)").text()
        artist = document.select("div.manga_series_data > div:eq(5)").text()
        val glist = document.select("div.series_sub_genre_list a").map { it.text() }
        genre = glist.joinToString(", ")
        description = document.select("div.manga_series_description p").text()
    }

    // Chapter

    override fun chapterListSelector(): String = "div.manga_series_list tbody tr"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select(" td:eq(0)").text()
        chapter_number = name.substringAfter("Chapter ").substringBefore(" -").toFloat()
        url = element.select("a").attr("href")
        date_upload = parseDate(element.select(" td:eq(1)").text())
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(date).time
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("img#gohere").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("src")))
        }
    }

    override fun imageUrlParse(document: Document): String {
        return throw Exception("Not Used")
    }

    // Filter

    private class Genre(name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        GenreList(getGenreList()))
    private fun getGenreList() = listOf(
        Genre("Act"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Ancients"),
        Genre("Animated"),
        Genre("Comedy"),
        Genre("Demons"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender Bender"),
        Genre("Harem"),
        Genre("Horror"),
        Genre("Josei"),
        Genre("Magic"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Military"),
        Genre("Mystery"),
        Genre("One Shot"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci Fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujoai"),
        Genre("Shounen"),
        Genre("Shounenai"),
        Genre("Slice Of Life"),
        Genre("Smut"),
        Genre("Sports"),
        Genre("Super Power"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Vampire"),
        Genre("Yaoi"),
        Genre("Yuri")
        )
}
