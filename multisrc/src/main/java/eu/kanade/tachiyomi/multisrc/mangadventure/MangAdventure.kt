package eu.kanade.tachiyomi.multisrc.mangadventure

import android.net.Uri
import android.os.Build.VERSION
import eu.kanade.tachiyomi.BuildConfig
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * MangAdventure base source.
 *
 * @property categories the available manga categories of the site.
 */
abstract class MangAdventure(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "en",
    val categories: List<String> = DEFAULT_CATEGORIES
) : HttpSource() {

    override val versionId = 1

    override val supportsLatest = true

    /** The full URL to the site's API. */
    open val apiUrl by lazy { "$baseUrl/api/v$versionId" }

    /**
     * A user agent representing Tachiyomi.
     * Includes the user's Android version
     * and the current extension version.
     */
    private val userAgent = "Mozilla/5.0 " +
        "(Android ${VERSION.RELEASE}; Mobile) " +
        "Tachiyomi/${BuildConfig.VERSION_NAME}"

    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", userAgent)
        add("Referer", baseUrl)
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl/releases/", headers)

    override fun pageListRequest(chapter: SChapter) =
        GET("$apiUrl/series/${chapter.path}", headers)

    override fun chapterListRequest(manga: SManga) =
        GET("$apiUrl/series/${manga.slug}/", headers)

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers)

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList
    ): Request {
        val uri = Uri.parse("$apiUrl/series/").buildUpon()
        if (query.startsWith(SLUG_QUERY)) {
            uri.appendQueryParameter("slug", query.substringAfter(SLUG_QUERY))
            return GET(uri.toString(), headers)
        }
        uri.appendQueryParameter("q", query)
        val cat = mutableListOf<String>()
        filters.forEach {
            when (it) {
                is Person -> uri.appendQueryParameter("author", it.state)
                is Status -> uri.appendQueryParameter("status", it.string())
                is CategoryList -> cat.addAll(
                    it.state.mapNotNull { c ->
                        Uri.encode(c.optString())
                    }
                )
                else -> Unit
            }
        }
        return GET("$uri&categories=${cat.joinToString(",")}", headers)
    }

    override fun latestUpdatesParse(response: Response) =
        json.parseToJsonElement(response.body!!.string()).run {
            MangasPage(jsonArray.map {
                val obj = it.jsonObject
                SManga.create().apply {
                    url = obj["url"]!!.jsonPrimitive.content
                    title = obj["title"]!!.jsonPrimitive.content
                    thumbnail_url = obj["cover"]!!.jsonPrimitive.content
                    // A bit of a hack to sort by date
                    val latest = obj["latest_chapter"]!!.jsonObject
                    description = httpDateToTimestamp(
                        latest["date"]!!.jsonPrimitive.content
                    ).toString()
                }
            }.sortedByDescending(SManga::description), false)
        }

    override fun chapterListParse(response: Response) =
        json.parseToJsonElement(response.body!!.string())
            .jsonObject["volumes"]!!.jsonObject.entries.flatMap { vol ->
                vol.value.jsonObject.entries.map { ch ->
                    SChapter.create().fromJSON(JsonObject(
                        ch.value.jsonObject.toMutableMap().also {
                            it["volume"] = JsonPrimitive(vol.key)
                            it["chapter"] = JsonPrimitive(ch.key)
                        }
                    ))
                }
            }

    override fun mangaDetailsParse(response: Response) =
        SManga.create().fromJSON(
            json.parseToJsonElement(response.body!!.string()).jsonObject
        )

    override fun pageListParse(response: Response) =
        json.parseToJsonElement(response.body!!.string()).jsonObject.run {
            val url = get("url")!!.jsonPrimitive.content
            val root = get("pages_root")!!.jsonPrimitive.content
            get("pages_list")!!.jsonArray.mapIndexed { i, e ->
                Page(i, "$url${i + 1}", "$root${e.jsonPrimitive.content}")
            }
        }

    override fun searchMangaParse(response: Response) =
        json.parseToJsonElement(response.body!!.string()).run {
            MangasPage(jsonArray.map {
                val obj = it.jsonObject
                SManga.create().apply {
                    url = obj["url"]!!.jsonPrimitive.content
                    title = obj["title"]!!.jsonPrimitive.content
                    thumbnail_url = obj["cover"]!!.jsonPrimitive.content
                }
            }.sortedBy(SManga::title), false)
        }

    override fun getFilterList() =
        FilterList(Person(), Status(), CategoryList())

    override fun fetchPopularManga(page: Int) =
        fetchSearchManga(page, "", FilterList())

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException(
            "This method should not be called!"
        )

    override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException(
            "This method should not be called!"
        )

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException(
            "This method should not be called!"
        )

    companion object {
        /** The possible statuses of a manga. */
        private val STATUSES = arrayOf("Any", "Completed", "Ongoing")

        /** Manga categories from MangAdventure `categories.xml` fixture. */
        internal val DEFAULT_CATEGORIES = listOf(
            "4-Koma",
            "Action",
            "Adventure",
            "Comedy",
            "Doujinshi",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Gender Bender",
            "Harem",
            "Hentai",
            "Historical",
            "Horror",
            "Josei",
            "Martial Arts",
            "Mecha",
            "Mystery",
            "Psychological",
            "Romance",
            "School Life",
            "Sci-Fi",
            "Seinen",
            "Shoujo",
            "Shoujo Ai",
            "Shounen",
            "Shounen Ai",
            "Slice of Life",
            "Smut",
            "Sports",
            "Supernatural",
            "Tragedy",
            "Yaoi",
            "Yuri"
        )

        /** Query to search by manga slug. */
        internal const val SLUG_QUERY = "slug:"

        /**
         * The HTTP date format specified in
         * [RFC 1123](https://tools.ietf.org/html/rfc1123#page-55).
         */
        private const val HTTP_DATE = "EEE, dd MMM yyyy HH:mm:ss zzz"

        /**
         * Converts a date in the [HTTP_DATE] format to a Unix timestamp.
         *
         * @param date The date to convert.
         * @return The timestamp of the date.
         */
        internal fun httpDateToTimestamp(date: String) =
            SimpleDateFormat(HTTP_DATE, Locale.US).parse(date)?.time ?: 0L
    }

    /**
     * Filter representing the status of a manga.
     *
     * @constructor Creates a [Filter.Select] object with [STATUSES].
     */
    inner class Status : Filter.Select<String>("Status", STATUSES) {
        /** Returns the [state] as a string. */
        fun string() = values[state].toLowerCase(Locale(lang))
    }

    /**
     * Filter representing a manga category.
     *
     * @property name The display name of the category.
     * @constructor Creates a [Filter.TriState] object using [name].
     */
    inner class Category(name: String) : Filter.TriState(name) {
        /** Returns the [state] as a string, or null if [isIgnored]. */
        fun optString() = when (state) {
            STATE_INCLUDE -> name.toLowerCase(Locale(lang))
            STATE_EXCLUDE -> "-" + name.toLowerCase(Locale(lang))
            else -> null
        }
    }

    /**
     * Filter representing the [categories][Category] of a manga.
     *
     * @constructor Creates a [Filter.Group] object with categories.
     */
    inner class CategoryList : Filter.Group<Category>(
        "Categories", categories.map(::Category)
    )

    /**
     * Filter representing the name of an author or artist.
     *
     * @constructor Creates a [Filter.Text] object.
     */
    inner class Person : Filter.Text("Author/Artist")
}
