package eu.kanade.tachiyomi.extension.en.homeheroscans

import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

open class HomeHeroScans : HttpSource() {

    final override val name = "Home Hero Scans"

    override val lang = "en"

    final override val baseUrl = "https://hhs.vercel.app"

    final override val supportsLatest = false

    private val json: Json by injectLazy()

    //    { seriesId |---> chapter |---> numPages }
    private val chapterNumberCache: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()

    /**
     * Given function f which returns an observable, returns a memoized version of f which'll
     * cache values emitted by the observable. Future calls will return an observable that
     * emits the cached values
     */
    private fun <P, R> memoizeObservable(f: (arg: P) -> Observable<R>): (P) -> Observable<R> {
        val cache = mutableMapOf<P, MutableList<R>>()
        fun decorated(arg: P) = cache[arg]?.let { Observable.from(it) } ?: f(arg).map {
            cache.getOrPut(arg, ::mutableListOf).add(it)
            it
        }
        return ::decorated
    }

    val memoizedFetchPopularManga = memoizeObservable { page: Int -> super.fetchPopularManga(page) }

    // Reduce number of times we call their api, user can force a call to api by relaunching the app
    override fun fetchPopularManga(page: Int) = memoizedFetchPopularManga(page)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/series.json", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonResult = json.parseToJsonElement(response.body!!.string()).jsonObject

        val mangaList = jsonResult.entries.map { entry ->
            val jsonObj = entry.value.jsonObject

            SManga.create().apply {
                artist = jsonObj["artist"]!!.jsonPrimitive.content
                author = jsonObj["author"]!!.jsonPrimitive.content
                description = jsonObj["description"]!!.jsonPrimitive.content
                genre = jsonObj["genre"]!!.jsonPrimitive.content
                title = jsonObj["title"]!!.jsonPrimitive.content
                thumbnail_url = baseUrl + jsonObj["cover"]!!.jsonPrimitive.content
                url = "/series?series=" + entry.key
                status = SManga.ONGOING
            }
        }

        return MangasPage(mangaList, hasNextPage = false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Search
    private fun getMangaId(s: String): String? {
        return s.toHttpUrlOrNull()?.let { url ->
            // allow for trailing slash
            if (url.pathSegments.size == 1 && url.pathSegments.last().isNotEmpty() || url.pathSegments.size == 2 && url.pathSegments.last().isEmpty())
                return url.queryParameter("series")
            return null
        }
    }

    private fun fetchBySeriesId(id: String): Observable<List<SManga>> = fetchPopularManga(1).map { mp ->
        mp.mangas.filter { "$baseUrl${it.url}".toHttpUrlOrNull()?.queryParameter("series") == id }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(URL_SEARCH_PREFIX))
        // Site doesn't have a search, so just return the popular page
            return fetchPopularManga(page)
        return getMangaId(query.substringAfter(URL_SEARCH_PREFIX))?.let { id ->
            fetchBySeriesId(id).map { MangasPage(it, false) }
        } ?: Observable.just(MangasPage(emptyList(), false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Chapter list (is paginated)
    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonResult = json.parseToJsonElement(response.body!!.string()).jsonObject

        return jsonResult["data"]!!.jsonArray
            .map { jsonEl ->
                val jsonObj = jsonEl.jsonObject
                val chapterData = jsonObj["data"]!!.jsonObject
                val series = chapterData["series"]!!.jsonPrimitive.content
                val chapter = chapterData["chapter"]!!.jsonPrimitive.content

                if (chapterNumberCache[series] == null) {
                    chapterNumberCache[series] = mutableMapOf()
                }

                chapterNumberCache[series]!![chapter] = chapterData["numPages"]!!.jsonPrimitive.content.toInt()

                SChapter.create().apply {
                    name = "Ch. $chapter ${chapterData["title"]!!.jsonPrimitive.content}"
                    chapter_number = chapter.toFloatOrNull() ?: -1f
                    date_upload = DATE_FORMATTER.parse(chapterData["date"]!!.jsonPrimitive.content)?.time ?: 0L
                    url = "/chapter?series=$series&ch=$chapter"
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val series = "$baseUrl${manga.url}".toHttpUrlOrNull()!!.queryParameter("series")
        return GET(
            "$baseUrl/api/chapters?series=$series", headers
        )
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        "$baseUrl${manga.url}".toHttpUrlOrNull()?.queryParameter("series")?.let { id ->
            fetchBySeriesId(id).map {
                (it.getOrNull(0) ?: manga).apply { initialized = true }
            }
        } ?: Observable.just(manga)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Default implementation of mangaDetailsRequest has to exist for webview to work
    // override fun mangaDetailsRequest(manga: SManga) = throw UnsupportedOperationException("Not used")

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val url = "$baseUrl${chapter.url}".toHttpUrlOrNull()!!
        val series = url.queryParameter("series")!!
        val chapternum = url.queryParameter("ch")!!
        fun chapterPages() = chapterNumberCache[series]?.get(chapternum)
        return if (chapterPages() != null) {
            Observable.just(chapterPages()!!)
        } else {
            // Has side effect of setting numPages in cache
            fetchChapterList(
                // Super hacky, url is wrong but has query parameter we need
                SManga.create().apply { this.url = chapter.url }
            ).map {
                chapterPages()
            }
        }.map { numpages ->
            (0 until numpages).toList().map {
                Page(it, "", "$baseUrl/api/image?key=$series/$chapternum/webp/$it.webp")
            }
        }
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter) = throw UnsupportedOperationException("Not Used")

    override fun imageUrlParse(response: Response): String = ""

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH)

        const val URL_SEARCH_PREFIX = "url:"
    }
}
