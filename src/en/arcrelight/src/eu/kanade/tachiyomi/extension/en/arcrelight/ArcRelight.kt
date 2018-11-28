package eu.kanade.tachiyomi.extension.en.arcrelight

import android.net.Uri
import android.os.Build.VERSION
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

/** Arc-Relight source */
class ArcRelight : HttpSource() {
    override val versionId = 1

    override val name = "Arc-Relight"

    override val baseUrl = "https://arc-relight.site/api/v$versionId"

    override val lang = "en"

    override val supportsLatest = true

    /**
     * A user agent representing Tachiyomi.
     * Includes the user's Android version
     * and the current extension version.
     */
    private val userAgent = "Mozilla/5.0 (" +
            "Android ${VERSION.RELEASE}; Mobile) " +
            "Tachiyomi/${BuildConfig.VERSION_NAME}"

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", userAgent)
        add("Referer", baseUrl)
    }

    override fun latestUpdatesRequest(page: Int) =
            GET("$baseUrl/releases/", headers)

    override fun pageListRequest(chapter: SChapter) =
            GET(Uri.parse(chapter.url).path.replace(
                    "/reader/", "$baseUrl/series/"), headers)

    override fun mangaDetailsRequest(manga: SManga) =
            GET("$baseUrl/series/${manga.url.split("/")
                    .last { it != "" }}/", headers)

    override fun chapterListRequest(manga: SManga) =
            mangaDetailsRequest(manga)

    override fun searchMangaRequest(page: Int, query: String,
                                    filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/series/").buildUpon()
        uri.appendQueryParameter("q", query)
        val cat = mutableListOf<String>()
        filters.forEach {
            when (it) {
                is Person -> uri.appendQueryParameter("author", it.state)
                is Status -> uri.appendQueryParameter("status", it.string())
                is CategoryList -> cat.addAll(it.state.mapNotNull {
                    c -> Uri.encode(c.stringOpt())
                })
            }
        }
        return GET("$uri&categories=${cat.joinToString(",")}", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val arr = JSONArray(response.body()!!.string())
        val ret = ArrayList<SManga>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            ret.add(SManga.create().apply {
                url = obj.getString("url")
                title = obj.getString("title")
                thumbnail_url = obj.getString("cover")
            })
        }
        return MangasPage(ret, false)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = JSONObject(response.body()!!.string())
        val volumes = res.getJSONObject("volumes")
        val ret = mutableListOf<SChapter>()
        volumes.keys().forEach { vol ->
            val chapters = volumes.getJSONObject(vol)
            chapters.keys().forEach { ch ->
                val obj = chapters.getJSONObject(ch)
                obj.put("chapter", ch)
                obj.put("volume", vol)
                ret.add(SChapter.create().apply { fromJSON(obj) })
            }
        }
        return ret.sortedByDescending { it.name }
    }

    override fun mangaDetailsParse(response: Response) =
            SManga.create().apply {
                fromJSON(JSONObject(response.body()!!.string()))
            }

    override fun pageListParse(response: Response): List<Page> {
        val obj = JSONObject(response.body()!!.string())
        val url = obj.getString("url")
        val root = obj.getString("pages_root")
        val arr = obj.getJSONArray("pages_list")
        val ret = mutableListOf<Page>()
        for (i in 0 until arr.length()) {
            ret.add(Page(i, "$url${i + 1}", root + arr.getString(i)))
        }
        return ret
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val arr = JSONArray(response.body()!!.string())
        val ret = mutableListOf<SManga>()
        for (i in 0 until arr.length()) {
            ret.add(SManga.create().apply {
                fromJSON(arr.getJSONObject(i))
            })
        }
        return MangasPage(ret.sortedBy { it.title }, false)
    }

    override fun getFilterList() = FilterList(
            Person(), Status(), CategoryList()
    )

    override fun fetchPopularManga(page: Int) =
            fetchSearchManga(page, "", FilterList())

    override fun popularMangaRequest(page: Int) =
            throw UnsupportedOperationException(
                    "This method should not be called!")

    override fun popularMangaParse(response: Response) =
            throw UnsupportedOperationException(
                    "This method should not be called!")

    override fun imageUrlParse(response: Response) =
            throw UnsupportedOperationException(
                    "This method should not be called!")
}

