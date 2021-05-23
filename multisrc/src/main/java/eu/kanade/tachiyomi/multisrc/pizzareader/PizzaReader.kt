package eu.kanade.tachiyomi.multisrc.pizzareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import rx.Observable

abstract class PizzaReader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val apiPath: String = "/api"
) : HttpSource() {

    override val supportsLatest = true

    open val apiUrl by lazy { "$baseUrl$apiPath" }

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/comics", headers)

    override fun popularMangaParse(response: Response) =
        MangasPage(
            JSONObject(response.asString()).run {
                val arr = getJSONArray("comics")
                (0 until arr.length()).map {
                    SManga.create().fromJSON(arr.getJSONObject(it))
                }
            },
            false
        )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$apiUrl/search/$query", headers)

    override fun searchMangaParse(response: Response) =
        MangasPage(
            JSONObject(response.asString()).run {
                val arr = getJSONArray("comics")
                (0 until arr.length()).map {
                    SManga.create().fromJSON(arr.getJSONObject(it))
                }
            },
            false
        )

    // TODO
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga =
        SManga.create().fromJSON(JSONObject(response.asString()).getJSONObject("comic"))

    override fun chapterListRequest(manga: SManga) = GET("$apiUrl${manga.url}", headers)

    override fun chapterListParse(response: Response) =
        JSONObject(response.asString()).getJSONObject("comic").run {
            val arr = getJSONArray("chapters")
            (0 until arr.length()).map {
                SChapter.create().fromJSON(arr.getJSONObject(it))
            }
        }

    override fun pageListRequest(chapter: SChapter) =
        GET("$apiUrl${chapter.url}", headers)

    override fun pageListParse(response: Response) =
        JSONObject(response.asString()).getJSONObject("chapter").run {
            val arr = getJSONArray("pages")
            (0 until arr.length()).map {
                Page(it, "", arr.getString(it))
            }
        }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

}
