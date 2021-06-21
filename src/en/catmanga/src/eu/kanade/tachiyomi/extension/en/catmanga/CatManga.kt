package eu.kanade.tachiyomi.extension.en.catmanga

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.HttpURLConnection

class CatManga : HttpSource() {

    private val application: Application by injectLazy()

    override val name = "CatManga"
    override val baseUrl = "https://catmanga.org"
    override val supportsLatest = true
    override val lang = "en"
    private val json: Json by injectLazy()

    private lateinit var seriesCache: LinkedHashMap<String, JsonSeries> // LinkedHashMap to preserve insertion order
    private lateinit var latestSeries: List<String>
    override val client = super.client.newBuilder().addInterceptor { chain ->
        // An interceptor which facilitates caching the data retrieved from the homepage
        when (chain.request().url) {
            doNothingRequest.url -> Response.Builder().body(
                "".toResponseBody("text/plain; charset=utf-8".toMediaType())
            ).code(HttpURLConnection.HTTP_NO_CONTENT).message("").protocol(Protocol.HTTP_1_0).request(chain.request()).build()
            homepageRequest.url -> {
                /* Homepage embeds a Json Object with information about every single series in the service */
                val response = chain.proceed(chain.request())
                val responseBody = response.peekBody(Long.MAX_VALUE).string()
                val seriesList = response.asJsoup(responseBody).getDataJsonObject()["props"]!!.jsonObject["pageProps"]!!.jsonObject["series"]!!
                val latests = response.asJsoup(responseBody).getDataJsonObject()["props"]!!.jsonObject["pageProps"]!!.jsonObject["latests"]!!
                seriesCache = linkedMapOf(
                    *json.decodeFromJsonElement<List<JsonSeries>>(seriesList).map { it.series_id to it }.toTypedArray()
                )
                latestSeries = json.decodeFromJsonElement<List<List<JsonElement>>>(latests).map { json.decodeFromJsonElement<JsonSeries>(it[0]).series_id }
                response
            }
            else -> chain.proceed(chain.request())
        }
    }.build()

    private val homepageRequest = GET(baseUrl)
    private val doNothingRequest = GET("https://dev.null")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = if (this::seriesCache.isInitialized) doNothingRequest else homepageRequest
    override fun popularMangaRequest(page: Int) = if (this::seriesCache.isInitialized) doNothingRequest else homepageRequest
    override fun latestUpdatesRequest(page: Int) = if (this::seriesCache.isInitialized) doNothingRequest else homepageRequest
    override fun chapterListRequest(manga: SManga) = homepageRequest

    private fun idOf(manga: SManga) = manga.url.substringAfterLast("/")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map {
                val manga = seriesCache.asSequence().map { it.value }.filter {
                    if (query.startsWith(SERIES_ID_SEARCH_PREFIX)) {
                        return@filter it.series_id.contains(query.removePrefix(SERIES_ID_SEARCH_PREFIX), true)
                    }
                    sequence { yieldAll(it.alt_titles); yield(it.title) }
                        .any { title -> title.contains(query, true) }
                }.map { it.toSManga() }.toList()

                MangasPage(manga, false)
            }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(homepageRequest)
        .asObservableSuccess()
        .map { seriesCache[idOf(manga)]?.toSManga() ?: manga }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val seriesId = manga.url.substringAfter("/series/")
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map {
                val seriesPrefs = application.getSharedPreferences("source_${id}_time_found:$seriesId", 0)
                val seriesPrefsEditor = seriesPrefs.edit()
                val cl = seriesCache[idOf(manga)]!!.chapters.asReversed().map {
                    val title = it.title ?: ""
                    val groups = it.groups.joinToString(", ")
                    val number = it.number.content
                    val displayNumber = it.display_number ?: number
                    SChapter.create().apply {
                        url = "${manga.url}/$number"
                        chapter_number = number.toFloat()
                        name = "Chapter $displayNumber" + if (title.isNotBlank()) " - $title" else ""
                        scanlator = groups

                        // Save current time when a chapter is found for the first time, and reuse it on future checks to
                        // prevent manga entry without any new chapter bumped to the top of "Latest chapter" list
                        // when the library is updated.
                        val currentTimeMillis = System.currentTimeMillis()
                        if (!seriesPrefs.contains(number)) {
                            seriesPrefsEditor.putLong(number, currentTimeMillis)
                        }
                        date_upload = seriesPrefs.getLong(number, currentTimeMillis)
                    }
                }
                seriesPrefsEditor.apply()
                cl
            }
    }

    override fun popularMangaParse(response: Response) = MangasPage(seriesCache.map { it.value.toSManga() }, false)

    override fun latestUpdatesParse(response: Response) = MangasPage(
        latestSeries.map { seriesCache[it]!!.toSManga() },
        false
    )

    override fun pageListParse(response: Response): List<Page> {
        return json.decodeFromJsonElement<List<String>>(response.asJsoup().getDataJsonObject()["props"]!!.jsonObject["pageProps"]!!.jsonObject["pages"]!!).mapIndexed { index, s ->
            Page(index, "", s)
        }
    }

    /**
     * Returns json object of site data
     */
    private fun Document.getDataJsonObject() = json.parseToJsonElement(getElementById("__NEXT_DATA__").html()).jsonObject

    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException("Not used.")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException("Not used.")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException("Not used.")
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used.")
    }

    companion object {
        const val SERIES_ID_SEARCH_PREFIX = "series_id:"
    }
}

@Serializable
private data class JsonImage(val source: String, val width: Int, val height: Int)

@Serializable
private data class JsonChapter(val title: String? = null, val groups: List<String>, val number: JsonPrimitive, val display_number: String? = null, val volume: Int? = null)

@Serializable
private data class JsonSeries(val alt_titles: List<String>, val authors: List<String>, val genres: List<String>, val chapters: List<JsonChapter>, val title: String, val series_id: String, val description: String, val status: String, val cover_art: JsonImage, val all_covers: List<JsonImage>) {
    fun toSManga() = this.let { jsonSeries ->
        SManga.create().apply {
            url = "/series/${jsonSeries.series_id}"
            title = jsonSeries.title
            thumbnail_url = jsonSeries.cover_art.source
            author = jsonSeries.authors.joinToString(", ")
            description = jsonSeries.description
            genre = jsonSeries.genres.joinToString(", ")
            status = when (jsonSeries.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }
}
