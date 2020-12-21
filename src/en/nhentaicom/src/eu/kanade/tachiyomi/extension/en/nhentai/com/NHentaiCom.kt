package eu.kanade.tachiyomi.extension.en.nhentai.com

import android.app.Application
import android.content.SharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Nsfw
class NHentaiCom : HttpSource() {

    override val name = "nHentai.com (unoriginal)"

    override val baseUrl = "https://nhentai.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        val mangas = jsonObject["data"].asJsonArray.map { json ->
            SManga.create().apply {
                title = json["title"].string
                thumbnail_url = json["image_url"].string
                url = json["slug"].string
            }
        }

        return MangasPage(mangas, jsonObject["current_page"].int < jsonObject["last_page"].int)
    }
    private fun getMangaUrl(url: String): String {
        return HttpUrl.parse(url)!!.newBuilder()
            .setQueryParameter("nsfw", "false").toString()
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET(getMangaUrl("$baseUrl/api/comics?page=$page&q=&sort=popularity&order=desc&duration=week"), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(getMangaUrl("$baseUrl/api/comics?page=$page&q=&sort=uploaded_at&order=desc&duration=day"), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/api/comics")!!.newBuilder()
            .addQueryParameter("per_page", "18")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", "desc")
            .addQueryParameter("q", query)
            .addQueryParameter("nsfw", "false")

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is DurationFilter -> url.addQueryParameter("duration", filter.toUriPart())
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Details

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(apiMangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET(getMangaUrl("$baseUrl/en/webtoon/${manga.url}"), headers)

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET(getMangaUrl("$baseUrl/api/comics/${manga.url}"), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return SManga.create().apply {
            description = jsonObject["description"].nullString
            status = jsonObject["status"].nullString.toStatus()
            thumbnail_url = jsonObject["image_url"].nullString
            genre = try { jsonObject["tags"].asJsonArray.joinToString { it["name"].string } } catch (_: Exception) { null }
            artist = try { jsonObject["artists"].asJsonArray.joinToString { it["name"].string } } catch (_: Exception) { null }
            author = try { jsonObject["authors"].asJsonArray.joinToString { it["name"].string } } catch (_: Exception) { null }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("complete", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "chapter"
                    url = manga.url
                }
            )
        )
    }

    override fun chapterListRequest(manga: SManga): Request = throw Exception("not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(getMangaUrl("$baseUrl/api/comics/${chapter.url}/images"), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["images"].asJsonArray.mapIndexed { i, json ->
            Page(i, "", json["source_url"].string)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        DurationFilter(getDurationList()),
        SortFilter(getSortList())
    )

    private class DurationFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Duration", pairs)

    private class SortFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Sorted by", pairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun getDurationList() = arrayOf(
        Pair("All time", "all"),
        Pair("Year", "year"),
        Pair("Month", "month"),
        Pair("Week", "week"),
        Pair("Day", "day")
    )

    private fun getSortList() = arrayOf(
        Pair("Popularity", "popularity"),
        Pair("Date", "uploaded_at")
    )
}
