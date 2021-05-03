package eu.kanade.tachiyomi.extension.fr.japanread

import android.net.Uri
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import rx.Observable
import java.util.Calendar

class Japanread : ParsedHttpSource() {

    override val name = "Japanread"

    override val baseUrl = "https://www.japanread.cc"

    override val lang = "fr"

    override val supportsLatest = true

    // Generic (used by popular/latest/search)
    private fun mangaListFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("img").attr("alt")
            setUrlWithoutDomain(element.select("a").attr("abs:href"))
            thumbnail_url = element.select("img").attr("src").replace("manga_medium", "manga_large")
        }
    }

    private fun mangaListSelector() = "div#manga-container div.col-lg-6"
    private fun mangaListNextPageSelector() = "a[rel=next]"

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list?sortType=9&page=$page", headers)
    }

    override fun popularMangaSelector() = mangaListSelector()
    override fun popularMangaFromElement(element: Element) = mangaListFromElement(element)
    override fun popularMangaNextPageSelector() = mangaListNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga-list?sortType=0&page=$page", headers)
    }

    override fun latestUpdatesSelector() = mangaListSelector()
    override fun latestUpdatesFromElement(element: Element) = mangaListFromElement(element)
    override fun latestUpdatesNextPageSelector() = mangaListNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // If there is any search text, use text search, otherwise use filter search
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search")
                .buildUpon()
                .appendQueryParameter("q", query)
        } else {
            val uri = Uri.parse("$baseUrl/manga-list").buildUpon()
            // Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri
        }
        // Append page number
        uri.appendQueryParameter("page", page.toString())
        return GET(uri.toString())
    }

    override fun searchMangaSelector() = mangaListSelector()
    override fun searchMangaFromElement(element: Element) = mangaListFromElement(element)
    override fun searchMangaNextPageSelector() = mangaListNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.card-header").text()
            artist = document.select("div.col-lg-3:contains(Artiste) + div").text()
            author = document.select("div.col-lg-3:contains(Auteur) + div").text()
            description = document.select("div.col-lg-3:contains(Description) + div").text()
            genre = document.select("div.col-lg-3:contains(Type - Catégories) + div .badge").joinToString { it.text() }
            status = document.select("div.col-lg-3:contains(Statut) + div").text().let {
                when {
                    it.contains("En cours") -> SManga.ONGOING
                    it.contains("Terminé") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = document.select("img[alt=couverture manga]").attr("src")
        }
    }

    private fun apiHeaders() = headersBuilder().apply {
        add("Referer", baseUrl)
        add("x-requested-with", "XMLHttpRequest")
    }.build()

    // Chapters
    // Subtract relative date
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringAfter("Il y a").trim().split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "ans" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "an" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "mois" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "sem." -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "j" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "h" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "min" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "s" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    override fun chapterListSelector() = "#chapters div[data-row=chapter]"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("div.col-lg-5 a").text()
            setUrlWithoutDomain(element.select("div.col-lg-5 a").attr("href"))
            date_upload = parseRelativeDate(element.select("div.order-lg-8").text())
            scanlator = element.select(".chapter-list-group a").joinToString { it.text() }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val requestUrl = if (manga.url.startsWith("http")) {
            "${manga.url}?page="
        } else {
            "$baseUrl${manga.url}?page="
        }
        return client.newCall(GET(requestUrl, headers))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, requestUrl)
            }
    }

    private fun chapterListParse(response: Response, requestUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        var moreChapters = true
        var nextPage = 2

        // chapters are paginated
        while (moreChapters) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            if (!document.select("a[rel=next]").isNullOrEmpty()) {
                document = client.newCall(GET(requestUrl + nextPage, headers)).execute().asJsoup()
                nextPage++
            } else {
                moreChapters = false
            }
        }
        return chapters
    }

    // Alternative way through API in case jSoup doesn't work anymore
    // It gives precise timestamp, but we are not using it
    // since the API wrongly returns null for the scanlation group
    /*private fun getChapterName(jsonElement: JsonElement): String {
        var name = ""

        if (jsonElement["volume"].asString != "") {
            name += "Tome " + jsonElement["volume"].asString + " "
        }
        if (jsonElement["chapter"].asString != "") {
            name += "Ch " + jsonElement["chapter"].asString + " "
        }

        if (jsonElement["title"].asString != "") {
            if (name != "") {
                name += " - "
            }
            name += jsonElement["title"].asString
        }

        return name
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.select("div[data-avg]").attr("data-avg")

        client.newCall(GET(baseUrl + document.select("#chapters div[data-row=chapter]").first().select("div.col-lg-5 a").attr("href"), headers)).execute()

        val apiResponse = client.newCall(GET("$baseUrl/api/?id=$mangaId&type=manga", apiHeaders())).execute()

        val jsonData = apiResponse.body!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject

        return json["chapter"].obj.entrySet()
            .map {
                SChapter.create().apply {
                    name = getChapterName(it.value.obj)
                    url = "$baseUrl/api/?id=${it.key}&type=chapter"
                    date_upload = it.value.obj["timestamp"].asLong * 1000
                    // scanlator = element.select(".chapter-list-group a").joinToString { it.text() }
                }
            }
            .sortedByDescending { it.date_upload }
    }
    override fun chapterListSelector() = throw UnsupportedOperationException("Not Used")
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not Used")*/

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(document: Document): List<Page> {
        val chapterId = document.select("meta[data-chapter-id]").attr("data-chapter-id")

        val apiResponse = client.newCall(GET("$baseUrl/api/?id=$chapterId&type=chapter", apiHeaders())).execute()

        val jsonData = apiResponse.body!!.string()
        val json = JsonParser.parseString(jsonData).asJsonObject

        val baseImagesUrl = json["baseImagesUrl"].string

        return json["page_array"].asJsonArray.mapIndexed { idx, it ->
            val imgUrl = "$baseUrl$baseImagesUrl/${it.asString}"
            Page(idx, baseUrl, imgUrl)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    // Filters
    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter()
    )

    private class SortFilter : UriSelectFilter(
        "Tri",
        "sortType",
        arrayOf(
            Pair("9", "Les + vus"),
            Pair("7", "Les mieux notés"),
            Pair("2", "A - Z"),
            Pair("5", "Les + commentés"),
            Pair("0", "Les + récents"),
        ),
        firstIsUnspecified = false,
    )

    private class TypeFilter : UriSelectFilter(
        "Type",
        "withTypes",
        arrayOf(
            Pair("0", "Tous"),
            Pair("2", "Manga"),
            Pair("3", "Manhwa"),
            Pair("4", "Manhua"),
            Pair("5", "Novel"),
            Pair("6", "Doujinshi")
        )
    )

    private class StatusFilter : UriSelectFilter(
        "Statut",
        "status",
        arrayOf(
            Pair("0", "Tous"),
            Pair("1", "En cours"),
            Pair("2", "Terminé")
        )
    )

    private class GenreFilter : UriSelectFilter(
        "Genre",
        "withCategories",
        arrayOf(
            Pair("0", "Tous"),
            Pair("1", "Action"),
            Pair("27", "Adulte"),
            Pair("20", "Amitié"),
            Pair("21", "Amour"),
            Pair("7", "Arts martiaux"),
            Pair("3", "Aventure"),
            Pair("6", "Combat"),
            Pair("5", "Comédie"),
            Pair("4", "Drame"),
            Pair("12", "Ecchi"),
            Pair("16", "Fantastique"),
            Pair("29", "Gender Bender"),
            Pair("8", "Guerre"),
            Pair("22", "Harem"),
            Pair("23", "Hentai"),
            Pair("15", "Historique"),
            Pair("19", "Horreur"),
            Pair("13", "Josei"),
            Pair("30", "Mature"),
            Pair("18", "Mecha"),
            Pair("32", "One-shot"),
            Pair("42", "Parodie"),
            Pair("17", "Policier"),
            Pair("25", "Science-fiction"),
            Pair("31", "Seinen"),
            Pair("10", "Shojo"),
            Pair("26", "Shojo Ai"),
            Pair("2", "Shonen"),
            Pair("35", "Shonen Ai"),
            Pair("37", "Smut"),
            Pair("14", "Sports"),
            Pair("38", "Surnaturel"),
            Pair("39", "Tragédie"),
            Pair("36", "Tranches de vie"),
            Pair("34", "Vie scolaire"),
            Pair("24", "Yaoi"),
            Pair("41", "Yuri"),
        )
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    private open class UriSelectFilter(
        displayName: String,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
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
