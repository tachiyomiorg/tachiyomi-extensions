package eu.kanade.tachiyomi.extension.en.homeheroscans

import com.github.salomonbrys.kotson.forEach
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat

open class HomeHeroScans : HttpSource() {
    override val lang = "en"
    final override val name = "Home Hero Scans"
    final override val baseUrl = "https://hhs.vercel.app"
    final override val supportsLatest = false

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
    // reduce number of times we call their api, user can force a call to api by relaunching the app
    override fun fetchPopularManga(page: Int) = memoizedFetchPopularManga(page)
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/series.json", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val res = JsonParser.parseString(response.body?.string()).asJsonObject
        val manga = mutableListOf<SManga>()
        res.forEach { s, jsonElement ->
            val data = jsonElement.asJsonObject
            fun get(k: String) = data[k]?.asString

            manga.add(
                SManga.create().apply {
                    artist = get("artist")
                    author = get("author")
                    description = get("description")
                    genre = get("genre")
                    title = get("title")!!
                    thumbnail_url = "$baseUrl${get("cover")}"
                    url = "/series?series=$s"
                    status = SManga.ONGOING // isn't reported
                }
            )
        }
        return MangasPage(manga, false)
    }

    // latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not used")

    // search

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
        // site doesn't have a search, so just return the popular page
            return fetchPopularManga(page)
        return getMangaId(query.substringAfter(URL_SEARCH_PREFIX))?.let { id ->
            fetchBySeriesId(id).map { MangasPage(it, false) }
        } ?: Observable.just(MangasPage(emptyList(), false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not used")
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException("Not used")

    // chapter list (is paginated),
    override fun chapterListParse(response: Response): List<SChapter> {
        return JsonParser.parseString(response.body?.string()!!).asJsonObject["data"].asJsonArray.map {
            val chapterData = it.asJsonObject["data"].asJsonObject
            fun get(k: String) = chapterData[k].asString
            if (chapterNumberCache[get("series")] == null)
                chapterNumberCache[get("series")] = mutableMapOf()
            chapterNumberCache[get("series")]!![get("chapter")] = get("numPages").toInt()
            SChapter.create().apply {
                url = "/chapter?series=${get("series")}&ch=${get("chapter")}"

                name = "Ch. ${get("chapter")} ${get("title")}"

                date_upload = SimpleDateFormat("MM/dd/yyyy").parse(get("date")).time

                chapter_number = get("chapter").toFloat()
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val series = "$baseUrl${manga.url}".toHttpUrlOrNull()!!.queryParameter("series")
        return POST(
            "$baseUrl/api/chapters", headers, """{"series":"$series"}""".toRequestBody("text/plain;charset=utf-8".toMediaTypeOrNull())
        )
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        "$baseUrl${manga.url}".toHttpUrlOrNull()?.queryParameter("series")?.let { id ->
            fetchBySeriesId(id).map {
                (it.getOrNull(0) ?: manga).apply { initialized = true }
            }
        } ?: Observable.just(manga)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException("Not used")
    // default implementation of mangaDetailsRequest has to exist for webview to work
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
            // has side effect of setting numPages in cache
            fetchChapterList(
                // super hacky, url is wrong but has query parameter we need
                SManga.create().apply { this.url = chapter.url }
            ).map {
                chapterPages()
            }
        }.map { numpages ->
            (0 until numpages).toList().map {
                Page(it, "", "https://raw.githubusercontent.com/kbhuynh/mhh-chapters/master/$series/$chapternum/$it.png")
            }
        }
    }

    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Not used")
    override fun pageListRequest(chapter: SChapter) = throw UnsupportedOperationException("Not Used")
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
    }
}
