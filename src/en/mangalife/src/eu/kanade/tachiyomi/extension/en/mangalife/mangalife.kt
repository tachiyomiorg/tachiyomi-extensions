package eu.kanade.tachiyomi.extension.en.mangalife

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Source responds to requests with their full database as a JsonArray, then sorts/filters it client-side
 * We'll take the database on first requests, then do what we want with it
 */

class MangaLife : HttpSource() {

    override val name = "MangaLife"

    override val baseUrl = "https://manga4life.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:71.0) Gecko/20100101 Firefox/71.0")

    private val gson = GsonBuilder().setLenient().create()

    private lateinit var directory: List<JsonElement>

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { response ->
                    popularMangaParse(response)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search/", headers)
    }

    // don't use ";" for substringBefore() !
    private fun directoryFromResponse(response: Response): String {
        return response.asJsoup().select("script:containsData(MainFunction)").first().data()
            .substringAfter("vm.Directory = ").substringBefore("vm.GetIntValue").trim()
            .replace(";", " ")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        directory = gson.fromJson<JsonArray>(directoryFromResponse(response))
            .sortedByDescending { it["v"].string }
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val mangas = mutableListOf<SManga>()
        val endRange = ((page * 24) - 1).let { if (it <= directory.lastIndex) it else directory.lastIndex }

        for (i in (((page - 1) * 24) .. endRange)){
            mangas.add(SManga.create().apply {
                title = directory[i]["s"].string
                url = "/manga/${directory[i]["i"].string}"
                thumbnail_url = "https://static.mangaboss.net/cover/${directory[i]["i"].string}.jpg"
            })
        }
        return MangasPage(mangas, endRange < directory.lastIndex)
    }

    // Latest

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { response ->
                    latestUpdatesParse(response)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(1)

    override fun latestUpdatesParse(response: Response): MangasPage {
        directory = gson.fromJson<JsonArray>(directoryFromResponse(response))
            .sortedByDescending { it["lt"].string }
        return parseDirectory(1)
    }

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response, query, filters)
                }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    private fun searchMangaParse(response: Response, query: String, filters: FilterList): MangasPage {
        directory = gson.fromJson<JsonArray>(directoryFromResponse(response))
            .filter { it["s"].string.contains(query, ignoreCase = true) }

        val genres = mutableListOf<String>()
        val genresNo = mutableListOf<String>()
        var sortBy: String
        for (filter in if (filters.isEmpty()) getFilterList() else filters) {
            when (filter) {
                is Sort -> {
                    sortBy = when (filter.state?.index) {
                        1 -> "ls"
                        2 -> "v"
                        else -> "s"
                    }
                    directory = if (filter.state?.ascending != true) {
                        directory.sortedByDescending { it[sortBy].string }
                    } else {
                        directory.sortedByDescending { it[sortBy].string }.reversed()
                    }
                }
                is SelectField -> if (filter.state != 0) directory = when (filter.name) {
                    "Scan Status" -> directory.filter { it["ss"].string.contains(filter.values[filter.state], ignoreCase = true) }
                    "Publish Status" -> directory.filter { it["ps"].string.contains(filter.values[filter.state], ignoreCase = true) }
                    "Type" -> directory.filter { it["t"].string.contains(filter.values[filter.state], ignoreCase = true) }
                    else -> directory
                }
                is YearField -> if (filter.state.isNotEmpty()) directory = directory.filter { it["y"].string.contains(filter.state) }
                is AuthorField -> if (filter.state.isNotEmpty()) directory = directory.filter { e -> e["a"].asJsonArray.any { it.string.contains(filter.state, ignoreCase = true) } }
                is GenreList -> filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> genres.add(genre.name)
                        Filter.TriState.STATE_EXCLUDE -> genresNo.add(genre.name)
                    }
                }
            }
        }
        if (genres.isNotEmpty()) genres.map { genre -> directory = directory.filter { e -> e["g"].asJsonArray.any { it.string.contains(genre, ignoreCase = true) } } }
        if (genresNo.isNotEmpty()) genresNo.map { genre -> directory = directory.filterNot { e -> e["g"].asJsonArray.any { it.string.contains(genre, ignoreCase = true) } } }

        return parseDirectory(1)
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        return response.asJsoup().select("div.BoxBody > div.row").let { info ->
            SManga.create().apply {
                title = info.select("h1").text()
                author = info.select("li.list-group-item:has(span:contains(Author)) a").first()?.text()
                genre = info.select("li.list-group-item:has(span:contains(Genre)) a").joinToString { it.text() }
                status = info.select("li.list-group-item:has(span:contains(Status)) a:contains(publish)").text().toStatus()
                description = info.select("div.Content").text()
                thumbnail_url = info.select("img").attr("abs:src")
            }
        }
    }

    private fun String.toStatus() = when {
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Complete", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters - Mind special cases like decimal chapters (e.g. One Punch Man) and manga with seasons (e.g. The Gamer)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    private fun chapterURLEncode(e: String ):String {
        var index = ""
        val t = e.substring(0,1).toInt()
        if (1 != t) { index = "-index-$t" }
        val n = e.substring(1,e.length-1)
        var suffix = ""
        val path = e.substring(e.length-1).toInt()
        if (0 != path) {suffix = ".$path"}
        return "-chapter-$n$index$suffix.html"
    }
    
    private fun chapterImage(e: String): String {
        val a = e.substring(1,e.length-1)
        val b = e.substring(e.length-1).toInt()
        return if (b == 0) {
            a
        } else {
            "$a.$b"
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val vmChapters = response.asJsoup().select("script:containsData(MainFunction)").first().data()
            .substringAfter("vm.Chapters = ").substringBefore(";")

        return gson.fromJson<JsonArray>(vmChapters).map{ json ->
            val indexChapter = json["Chapter"].string
            SChapter.create().apply {
                name = json["ChapterName"].string.let { if (it.isNotEmpty()) it else "${json["Type"].string} ${chapterImage(indexChapter)}" }
                url = "/read-online/" + response.request().url().toString().substringAfter("/manga/") + chapterURLEncode(indexChapter)
                date_upload = try {
                    dateFormat.parse(json["Date"].string.substringBefore(" ")).time
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select("script:containsData(MainFunction)").first().data()
        val curChapter = gson.fromJson<JsonElement>(script.substringAfter("vm.CurChapter = ").substringBefore(";"))

        val pageTotal = curChapter["Page"].string.toInt()
        var chNum = chapterImage(curChapter["Chapter"].string)

        val titleURI = script.substringAfter("vm.IndexName = \"").substringBefore("\"")
        val seasonURI = curChapter["Directory"].string
            .let { if (it.isEmpty()) "" else "$it/" }

        val reqJSON = "{\"IndexName\":\"$titleURI\",\"Chapter\":\"$chNum\"}"
        val request = Request.Builder()
            .url("$baseUrl/read-online/fetch.php")
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),reqJSON))
            .build()
        val test = client.newCall(request).execute().body()!!.string().substringAfter("PathName\":\"").substringBefore("\"")
        val path = "https://$host/manga/$titleURI/$seasonURI"

        return IntRange(1, pageTotal).mapIndexed { i, _ ->
            var imageNum = (i + 1).toString().let { "000$it" }.let { it.substring(it.length-3) }
            Page(i, "", path + "$chNum-$imageNum.png")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    private class Sort : Filter.Sort("Sort", arrayOf("Alphabetically", "Date updated", "Popularity"), Selection(2, false))
    private class Genre(name: String) : Filter.TriState(name)
    private class YearField : Filter.Text("Years")
    private class AuthorField : Filter.Text("Author")
    private class SelectField(name: String, values: Array<String>, state: Int = 0) : Filter.Select<String>(name, values, state)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            YearField(),
            AuthorField(),
            SelectField("Scan Status", arrayOf("Any", "Complete", "Discontinued", "Hiatus", "Incomplete", "Ongoing")),
            SelectField("Publish Status", arrayOf("Any", "Cancelled", "Complete", "Discontinued", "Hiatus", "Incomplete", "Ongoing", "Unfinished")),
            SelectField("Type", arrayOf("Any", "Doujinshi", "Manga", "Manhua", "Manhwa", "OEL", "One-shot")),
            Sort(),
            GenreList(getGenreList())
    )

    // [...document.querySelectorAll("label.triStateCheckBox input")].map(el => `Filter("${el.getAttribute('name')}", "${el.nextSibling.textContent.trim()}")`).join(',\n')
    // http://mangalife.co/advanced-search/
    private fun getGenreList() = listOf(
            Genre("Action"),
            Genre("Adult"),
            Genre("Adventure"),
            Genre("Comedy"),
            Genre("Doujinshi"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Gender Bender"),
            Genre("Harem"),
            Genre("Hentai"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Josei"),
            Genre("Lolicon"),
            Genre("Martial Arts"),
            Genre("Mature"),
            Genre("Mecha"),
            Genre("Mystery"),
            Genre("Psychological"),
            Genre("Romance"),
            Genre("School Life"),
            Genre("Sci-fi"),
            Genre("Seinen"),
            Genre("Shotacon"),
            Genre("Shoujo"),
            Genre("Shoujo Ai"),
            Genre("Shounen"),
            Genre("Shounen Ai"),
            Genre("Slice of Life"),
            Genre("Smut"),
            Genre("Sports"),
            Genre("Supernatural"),
            Genre("Tragedy"),
            Genre("Yaoi"),
            Genre("Yuri")
    )

}
