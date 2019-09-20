package eu.kanade.tachiyomi.extension.en.tsumino

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getArtists
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getCollection
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getChapter
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getDesc
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Tsumino: ParsedHttpSource() {

    override val name = "Tsumino"

    override val baseUrl = "https://www.tsumino.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    override fun latestUpdatesSelector() = "Not needed"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/Search/Operate/?PageNumber=$page&Sort=Newest")

    override fun latestUpdatesParse(response: Response): MangasPage {
    val allManga = mutableListOf<SManga>()
        val body = response.body()!!.string()
        val jsonManga = gson.fromJson<JsonObject>(body)["data"].asJsonArray
        for (i in 0 until jsonManga.size()) {
            val manga = SManga.create()
            manga.url = "/entry/" + jsonManga[i]["entry"]["id"].asString
            manga.title = jsonManga[i]["entry"]["title"].asString
            manga.thumbnail_url = jsonManga[i]["entry"]["thumbnailUrl"].asString
            allManga.add(manga)
        }

        val currentPage = gson.fromJson<JsonObject>(body)["pageNumber"].asString
        val totalPage = gson.fromJson<JsonObject>(body)["pageCount"].asString
        val hasNextPage = currentPage.toInt() != totalPage.toInt()

        return MangasPage(allManga, hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element): SManga = throw  UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = "Not needed"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/Search/Operate/?PageNumber=$page&Sort=Popularity")

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Taken from github.com/NerdNumber9/TachiyomiEH
        val f = filters + getFilterList()
        val advSearch = f.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
            val splitState = filter.state.split(",").map(String::trim).filterNot(String::isBlank)
            splitState.map {
                AdvSearchEntry(filter.type, it.removePrefix("-"), it.startsWith("-"))
            }
        }
        val body = FormBody.Builder()
            .add("PageNumber", page.toString())
            .add("Text", query)
            .add("Sort", SortType.values()[f.filterIsInstance<SortFilter>().first().state].name)
            .add("List", "0")
            .add("Length", LengthType.values()[f.filterIsInstance<LengthFilter>().first().state].id.toString())
            .add("MinimumRating", f.filterIsInstance<MinimumRatingFilter>().first().state.toString())
            .apply {
                advSearch.forEachIndexed { index, entry ->
                    add("Tags[$index][Type]", entry.type.toString())
                    add("Tags[$index][Text]", entry.text)
                    add("Tags[$index][Exclude]", entry.exclude.toString())
                }

                if(f.filterIsInstance<ExcludeParodiesFilter>().first().state)
                    add("Exclude[]", "6")
            }
            .build()

        return POST("$baseUrl/Search/Operate/", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

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
        val infoElement = document.select("div.book-page-container")
        val manga = SManga.create()
        val genres = mutableListOf<String>()

        infoElement.select("#Tag a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }

        manga.title = infoElement.select("#Title").text()
        manga.artist = getArtists(document)
        manga.author = manga.artist
        manga.status = SManga.COMPLETED
        manga.genre = genres.joinToString(", ")
        manga.thumbnail_url = infoElement.select("img").attr("src")
        manga.description = getDesc(document)

        return manga
    }
    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val collection = document.select(chapterListSelector())
        return if (collection.isNotEmpty()) {
            getCollection(document, chapterListSelector())
        } else {
            getChapter(document, response)
        }
    }

    override fun chapterListSelector() = ".book-collection-table a"

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val numPages = document.select("h1").text().split(" ").last()

        if (numPages.isNotEmpty()) {
            for (i in 1 until numPages.toInt() + 1) {
                val data = document.select("#image-container").attr("data-cdn")
                    .replace("[PAGE]", i.toString())
                pages.add(Page(i, "", data))
            }
        } else {
            throw  UnsupportedOperationException("Error: Open in WebView and solve the Captcha!")
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    data class AdvSearchEntry(val type: Int, val text: String, val exclude: Boolean)

    override fun getFilterList() = FilterList(
            Filter.Header("Separate tags with commas (,)"),
            Filter.Header("Prepend with dash (-) to exclude"),
            TagFilter(),
            CategoryFilter(),
            CollectionFilter(),
            GroupFilter(),
            ArtistFilter(),
            ParodyFilter(),
            CharactersFilter(),
            UploaderFilter(),

            Filter.Separator(),

            SortFilter(),
            LengthFilter(),
            MinimumRatingFilter(),
            ExcludeParodiesFilter()
    )

    class TagFilter : AdvSearchEntryFilter("Tags", 1)
    class CategoryFilter : AdvSearchEntryFilter("Categories", 2)
    class CollectionFilter : AdvSearchEntryFilter("Collections", 3)
    class GroupFilter : AdvSearchEntryFilter("Groups", 4)
    class ArtistFilter : AdvSearchEntryFilter("Artists", 5)
    class ParodyFilter : AdvSearchEntryFilter("Parodies", 6)
    class CharactersFilter : AdvSearchEntryFilter("Characters", 7)
    class UploaderFilter : AdvSearchEntryFilter("Uploaders", 8)
    open class AdvSearchEntryFilter(name: String, val type: Int) : Filter.Text(name)

    class SortFilter : Filter.Select<SortType>("Sort by", SortType.values())
    class LengthFilter : Filter.Select<LengthType>("Length", LengthType.values())
    class MinimumRatingFilter : Filter.Select<String>("Minimum rating", (0 .. 5).map { "$it stars" }.toTypedArray())
    class ExcludeParodiesFilter : Filter.CheckBox("Exclude parodies")

    enum class SortType {
        Newest,
        Oldest,
        Alphabetical,
        Rating,
        Pages,
        Views,
        Random,
        Comments,
        Popularity
    }

    enum class LengthType(val id: Int) {
        Any(0),
        Short(1),
        Medium(2),
        Long(3)
    }

}
