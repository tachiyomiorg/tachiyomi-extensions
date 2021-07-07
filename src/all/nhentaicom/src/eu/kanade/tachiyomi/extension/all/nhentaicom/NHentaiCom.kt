package eu.kanade.tachiyomi.extension.all.nhentaicom

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

@Nsfw
class NHentaiCom(override val lang: String) : HttpSource() {

    override val name = when (lang) {
        "other" -> "nHentai.com (unoriginal)(Text Cleaned)"
        "all" -> "nHentai.com (unoriginal)(Unfiltered)"
        else -> "nHentai.com (unoriginal)"
    }

    override val id = when (lang) {
        "en" -> 5591830863732393712
        else -> super.id
    }

    override val baseUrl = "https://nhentai.com"

    private val langId = toLangId(lang)

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private fun toLangId(langCode: String): String {
        return when (langCode) {
            "en" -> "1"
            "zh" -> "2"
            "ja" -> "3"
            "other" -> "4"
            "cz" -> "5"
            "ar" -> "6"
            "sk" -> "7"
            "eo" -> "8"
            else -> ""
        }
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonRaw = response.body!!.string()
        val jsonResult = json.parseToJsonElement(jsonRaw).jsonObject

        val mangas = jsonResult["data"]!!.jsonArray.map { jsonEl ->
            SManga.create().apply {
                val jsonObj = jsonEl.jsonObject
                title = jsonObj["title"]!!.jsonPrimitive.content
                thumbnail_url = jsonObj["image_url"]!!.jsonPrimitive.content
                url = jsonObj["slug"]!!.jsonPrimitive.content
            }
        }

        return MangasPage(mangas, jsonResult["current_page"]!!.jsonPrimitive.content.toInt() < jsonResult["last_page"]!!.jsonPrimitive.content.toInt())
    }

    private fun getMangaUrl(page: Int, sort: String): String {
        val url = "$baseUrl/api/comics".toHttpUrlOrNull()!!.newBuilder()
        if (langId.isNotBlank()) {
            url.setQueryParameter("languages[]", langId)
        }
        url.setQueryParameter("page", "$page")
        url.setQueryParameter("sort", sort)
        url.setQueryParameter("nsfw", "false")
        return url.toString()
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET(getMangaUrl(page, "popularity"), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(getMangaUrl(page, "uploaded_at"), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaFromJson(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/comics".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("per_page", "18")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("nsfw", "false")

        if (langId.isNotBlank()) {
            url.setQueryParameter("languages[]", langId)
        }
        url.setQueryParameter("page", "$page")
        url.setQueryParameter("nsfw", "false")

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is DurationFilter -> url.addQueryParameter("duration", filter.toUriPart())
                is SortOrderFilter -> url.addQueryParameter("order", filter.toUriPart())
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
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/en/comic/${manga.url}", headers)

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/comics/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonRaw = response.body!!.string()
        val jsonObject = json.parseToJsonElement(jsonRaw).jsonObject

        return SManga.create().apply {
            description = jsonObject["description"]!!.jsonPrimitive.content
            status = SManga.COMPLETED
            thumbnail_url = jsonObject["image_url"]!!.jsonPrimitive.content
            genre = runCatching{ jsonObject["tags"]!!.jsonArray.joinToString { it.jsonObject["name"]!!.jsonPrimitive.content }}.getOrNull()
            artist = runCatching{ jsonObject["artists"]!!.jsonArray.joinToString { it.jsonObject["name"]!!.jsonPrimitive.content }}.getOrNull()
            author = runCatching{ jsonObject["authors"]!!.jsonArray.joinToString { it.jsonObject["name"]!!.jsonPrimitive.content }}.getOrNull()
        }
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
        return GET("$baseUrl/api/comics/${chapter.url}/images", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return json.parseToJsonElement(response.body!!.string()).jsonObject["images"]!!.jsonArray.mapIndexed { i, jsonEl ->
            val jsonObj = jsonEl.jsonObject
            Page(i, "", jsonObj["source_url"]!!.jsonPrimitive.content)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        DurationFilter(getDurationList()),
        SortFilter(getSortList()),
        SortOrderFilter(getSortOrder())
    )

    private class DurationFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Duration", pairs)

    private class SortFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Sorted by", pairs)

    private class SortOrderFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Order", pairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun getSortOrder() = arrayOf(
        Pair("Descending", "desc"),
        Pair("Ascending", "asc"),
    )

    private fun getDurationList() = arrayOf(
        Pair("All time", "all"),
        Pair("Year", "year"),
        Pair("Month", "month"),
        Pair("Week", "week"),
        Pair("Day", "day")
    )

    private fun getSortList() = arrayOf(
        Pair("Upload date", "uploaded_at"),
        Pair("Title", "title"),
        Pair("Pages", "pages"),
        Pair("Favorites", "favorites"),
        Pair("Popularity", "popularity"),
    )
}
