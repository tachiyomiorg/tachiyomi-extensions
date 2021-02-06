package eu.kanade.tachiyomi.extension.all.hentaihand

import android.annotation.SuppressLint
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.nullString
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.schedulers.Schedulers
import java.text.SimpleDateFormat

@Nsfw
class HentaiHand(override val lang: String, val hhLangId: Int) : HttpSource() {

    override val baseUrl: String = "https://hentaihand.com"
    override val name: String = "HentaiHand"
    override val supportsLatest = true

    private val gson = Gson()

    override val client: OkHttpClient = network.cloudflareClient

    private fun parseGenericResponse(response: Response): MangasPage {
        val data = gson.fromJson<JsonObject>(response.body()!!.string())
        return MangasPage(
            data.getAsJsonArray("data").map {
                SManga.create().apply {
                    url = "/en/comic/${it["slug"].asString}"
                    title = it["title"].asString
                    thumbnail_url = it["thumb_url"].asString
                }
            },
            !data["next_page_url"].isJsonNull
        )
    }

    // Popular

    override fun popularMangaParse(response: Response): MangasPage = parseGenericResponse(response)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/comics?page=$page&sort=popularity&order=desc&duration=all&languages=$hhLangId")
    }

    // Latest

    override fun latestUpdatesParse(response: Response): MangasPage = parseGenericResponse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/comics?page=$page&sort=uploaded_at&order=desc&duration=week&languages=$hhLangId")
    }

    // Search

    override fun searchMangaParse(response: Response): MangasPage = parseGenericResponse(response)

    private fun lookupFilterId(query: String, uri: String): Int? {
        // filter query needs to be resolved to an ID
        return client.newCall(GET("$baseUrl/api/$uri?q=$query"))
            .asObservableSuccess()
            .subscribeOn(Schedulers.io())
            .map {
                val data = gson.fromJson<JsonObject>(it.body()!!.string())
                // only the first tag will be used
                data.getAsJsonArray("data").firstOrNull()?.let { t -> t["id"].asInt }
            }.toBlocking().first()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {

        val url = HttpUrl.parse("$baseUrl/api/comics")!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("languages", hhLangId.toString())

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", getSortPairs()[filter.state].second)
                is OrderFilter -> url.addQueryParameter("order", getOrderPairs()[filter.state].second)
                is DurationFilter -> url.addQueryParameter("duration", getDurationPairs()[filter.state].second)
                is AttributesGroupFilter -> filter.state.forEach {
                    if (it.state) url.addQueryParameter("attributes", it.value)
                }
                is LookupFilter -> {
                    filter.state.split(",").map { it.trim() }.filter { it.isNotBlank() }.map {
                        lookupFilterId(it, filter.uri) ?: throw Exception("No ${filter.singularName} \"$it\" was found")
                    }.forEach {
                        if (!(filter.uri == "languages" && it == hhLangId))
                            url.addQueryParameter(filter.uri, it.toString())
                    }
                }
                else -> {}
            }
        }

        return GET(url.toString())
    }

    // Details

    private fun tagArrayToString(array: JsonArray, key: String = "name"): String? {
        if (array.size() == 0)
            return null
        return array.joinToString { it[key].asString }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val slug = manga.url.removePrefix("/en/comic/")
        return GET("$baseUrl/api/comics/$slug")
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
            .asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = gson.fromJson<JsonObject>(response.body()!!.string())
        return SManga.create().apply {

            artist = tagArrayToString(data.getAsJsonArray("artists"))
            author = tagArrayToString(data.getAsJsonArray("authors")) ?: artist

            genre = listOf("tags", "relationships").map {
                data.getAsJsonArray(it).map { t -> t["name"].asString }
            }.flatten().distinct().joinToString()

            status = SManga.COMPLETED

            description = listOf(
                Pair("Alternative Title", data["alternative_title"].nullString),
                Pair("Groups", tagArrayToString(data.getAsJsonArray("groups"))),
                Pair("Description", data["description"].nullString),
                Pair("Pages", data["pages"].asInt.toString()),
                Pair("Category", data["category"].nullObj?.get("name")?.asString),
                Pair("Language", data["language"].nullObj?.get("name")?.asString),
                Pair("Parodies", tagArrayToString(data.getAsJsonArray("parodies"))),
                Pair("Characters", tagArrayToString(data.getAsJsonArray("characters")))
            ).filter { !it.second.isNullOrEmpty() }.joinToString("\n\n") { "${it.first}:\n${it.second}" }
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = gson.fromJson<JsonObject>(response.body()!!.string())
        return listOf(
            SChapter.create().apply {
                url = "/en/comic/${data["slug"].asString}/reader/1"
                name = "Chapter"
                date_upload = DATE_FORMAT.parse(data["uploaded_at"].asString)?.time ?: 0
                chapter_number = 1f
            }
        )
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.removePrefix("/en/comic/").removeSuffix("/reader/1")
        return GET("$baseUrl/api/comics/$slug/images")
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = gson.fromJson<JsonObject>(response.body()!!.string())
        return data.getAsJsonArray("images").mapIndexed { i, it ->
            Page(i, "/en/comic/${data["comic"]["slug"].asString}/reader/${it["page"].asInt}", it["source_url"].asString)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    private class SortFilter(sortPairs: List<Pair<String, String>>) : Filter.Select<String>("Sort By", sortPairs.map { it.first }.toTypedArray())
    private class OrderFilter(orderPairs: List<Pair<String, String>>) : Filter.Select<String>("Order By", orderPairs.map { it.first }.toTypedArray())
    private class DurationFilter(durationPairs: List<Pair<String, String>>) : Filter.Select<String>("Duration", durationPairs.map { it.first }.toTypedArray())
    private class AttributeFilter(name: String, val value: String) : Filter.CheckBox(name)
    private class AttributesGroupFilter(attributePairs: List<Pair<String, String>>) : Filter.Group<AttributeFilter>("Attributes", attributePairs.map { AttributeFilter(it.first, it.second) })

    private class CategoriesFilter : LookupFilter("Categories", "categories", "category")
    private class TagsFilter : LookupFilter("Tags", "tags", "tag")
    private class ArtistsFilter : LookupFilter("Artists", "artists", "artist")
    private class GroupsFilter : LookupFilter("Groups", "groups", "group")
    private class CharactersFilter : LookupFilter("Characters", "characters", "character")
    private class ParodiesFilter : LookupFilter("Parodies", "parodies", "parody")
    private class LanguagesFilter : LookupFilter("Other Languages", "languages", "language")
    open class LookupFilter(name: String, val uri: String, val singularName: String) : Filter.Text(name)

    override fun getFilterList() = FilterList(
        SortFilter(getSortPairs()),
        OrderFilter(getOrderPairs()),
        DurationFilter(getDurationPairs()),
        Filter.Header("Separate terms with commas (,)"),
        CategoriesFilter(),
        TagsFilter(),
        ArtistsFilter(),
        GroupsFilter(),
        CharactersFilter(),
        ParodiesFilter(),
        LanguagesFilter(),
        AttributesGroupFilter(getAttributePairs())
    )

    private fun getSortPairs() = listOf(
        Pair("Upload Date", "uploaded_at"),
        Pair("Title", "title"),
        Pair("Pages", "pages"),
        Pair("Favorites", "favorites"),
        Pair("Popularity", "popularity")
    )

    private fun getOrderPairs() = listOf(
        Pair("Descending", "desc"),
        Pair("Ascending", "asc")
    )

    private fun getDurationPairs() = listOf(
        Pair("Today", "day"),
        Pair("This Week", "week"),
        Pair("This Month", "month"),
        Pair("This Year", "year"),
        Pair("All Time", "all")
    )

    private fun getAttributePairs() = listOf(
        Pair("Translated", "translated"),
        Pair("Speechless", "speechless"),
        Pair("Rewritten", "rewritten")
    )

    companion object {
        @SuppressLint("SimpleDateFormat")
        private val DATE_FORMAT = SimpleDateFormat("yyyy-dd-MM")
    }
}
