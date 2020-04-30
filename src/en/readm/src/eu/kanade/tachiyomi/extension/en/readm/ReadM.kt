package eu.kanade.tachiyomi.extension.en.readm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.util.Calendar
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ReadM : ParsedHttpSource() {

    // Info
    override val name: String = "ReadM"
    override val baseUrl: String = "https://readm.org"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular-manga/$page", headers)
    override fun popularMangaNextPageSelector(): String? = "div.pagination a:contains(»)"
    override fun popularMangaSelector(): String = "div#discover-response li"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select("div.subject-title a").first().apply {
            title = this.text().trim()
            url = this.attr("href")
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-releases/$page", headers)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = "ul.latest-updates li"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("data-src")
        element.select("a").first().apply {
            title = this.text().trim()
            url = this.attr("href")
        }
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val formBody = FormBody.Builder()
            .add("manga-name", query)
        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> formBody.add("type", typeArray[filter.state].second)
                is AuthorName -> formBody.add("author-name", filter.state)
                is ArtistName -> formBody.add("artist-name", filter.state)
                is StatusFilter -> formBody.add("status", statusArray[filter.state].second)
                is GenreFilter -> filter.state.forEach { genre ->
                    if (genre.isExcluded()) formBody.add("exclude[]", genre.id)
                    if (genre.isIncluded()) formBody.add("include[]", genre.id)
                }
            }
        }
        if (filters.isEmpty()) {
            formBody
                .add("type", "all")
                .add("status", "both")
        }
        val searchHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").build()
        return POST("$baseUrl/service/advanced_search", searchHeaders, formBody.build())
    }

    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector(): String = "div.poster-with-subject"
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("img.series-profile-thumb").attr("abs:src")
        title = document.select("h1.page-title").text().trim()
        author = document.select("span#first_episode a").text().trim()
        artist = document.select("span#last_episode a").text().trim()
        description = document.select("div.series-summary-wrapper p").text().trim()
        genre = document.select("div.series-summary-wrapper div.item a").joinToString(", ") { it.text().trim() }
    }

    // Chapters

    override fun chapterListSelector(): String = "div.season_start"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").text()
        url = element.select("a").attr("href")
        date_upload = parseChapterDate(element.select("td.episode-date").text().trim())
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")

        if (dateWords.size == 2) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val calendar = Calendar.getInstance()

            when {
                dateWords[1].contains("Minute") -> {
                    calendar.add(Calendar.MINUTE, -timeAgo)
                }
                dateWords[1].contains("Hour") -> {
                    calendar.add(Calendar.HOUR_OF_DAY, -timeAgo)
                }
                dateWords[1].contains("Day") -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Week") -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Month") -> {
                    calendar.add(Calendar.MONTH, -timeAgo)
                }
                dateWords[1].contains("Year") -> {
                    calendar.add(Calendar.YEAR, -timeAgo)
                }
            }

            return calendar.timeInMillis
        }

        return 0L
    }

    // Pages

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")
    override fun pageListParse(document: Document): List<Page> = document.select("div.ch-images img").mapIndexed { index, element ->
        Page(index, "", element.attr("abs:src"))
    }

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(typeArray),
        AuthorName(),
        ArtistName(),
        StatusFilter(statusArray),
        GenreFilter(genreArray())
    )

    private class TypeFilter(values: Array<Pair<String, String>>) : Filter.Select<String>("Type", values.map { it.first }.toTypedArray())
    private class AuthorName : Filter.Text("Author Name")
    private class ArtistName : Filter.Text("Artist Name")
    private class StatusFilter(values: Array<Pair<String, String>>) : Filter.Select<String>("Status", values.map { it.first }.toTypedArray())
    private class GenreFilter(state: List<Tag>) : Filter.Group<Tag>("Genres", state)
    private class Tag(name: String, val id: String) : Filter.TriState(name)

    private val typeArray = arrayOf(
        Pair("All", "all"),
        Pair("Japanese Manga", "japanese"),
        Pair("Korean Manhwa", "korean"),
        Pair("Chinese Manhua", "chinese")
    )

    private val statusArray = arrayOf(
        Pair("Both", "both"),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed")
    )

    private fun genreArray() = listOf(
        Tag("Action", "1"),
        Tag("Adventure", "23"),
        Tag("Comedy", "12"),
        Tag("Doujinshi", "26"),
        Tag("Drama", "9"),
        Tag("Ecchi", "2"),
        Tag("Fantasy", "3"),
        Tag("Gender Bender", "30"),
        Tag("Harem", "4"),
        Tag("Historical", "36"),
        Tag("Horror", "34"),
        Tag("Josei", "17"),
        Tag("Lolicon", "39"),
        Tag("Manga", "5"),
        Tag("Manhua", "31"),
        Tag("Manhwa", "32"),
        Tag("Martial Arts", "22"),
        Tag("Mecha", "33"),
        Tag("Mystery", "13"),
        Tag("None", "41"),
        Tag("One shot", "16"),
        Tag("Psychological", "14"),
        Tag("Romance", "6"),
        Tag("School Life", "10"),
        Tag("Sci fi", "19"),
        Tag("Sci-fi", "40"),
        Tag("Seinen", "24"),
        Tag("Shotacon", "38"),
        Tag("Shoujo", "8"),
        Tag("Shoujo Ai", "37"),
        Tag("Shounen", "7"),
        Tag("Shounen Ai", "35"),
        Tag("Slice of Life", "21"),
        Tag("Sports", "29"),
        Tag("Supernatural", "11"),
        Tag("Tragedy", "15"),
        Tag("Uncategorized", "43"),
        Tag("Yaoi", "28"),
        Tag("Yuri", "20")
    ).sortedWith(compareBy { it.name })
}
