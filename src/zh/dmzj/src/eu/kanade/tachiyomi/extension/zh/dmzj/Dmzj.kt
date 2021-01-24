package eu.kanade.tachiyomi.extension.zh.dmzj

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.ratelimit.SpecificHostRateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.util.ArrayList

/**
 * Dmzj source
 */

class Dmzj : ConfigurableSource, HttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "动漫之家"
    override val baseUrl = "https://m.dmzj1.com"
    private val apiUrl = "https://v3api.dmzj1.com"
    private val imageCDNUrl = "https://images.dmzj1.com"

    private fun cleanUrl(url: String) = if (url.startsWith("//"))
        "https:$url"
    else url

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val apiRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        HttpUrl.parse(apiUrl)!!,
        preferences.getString(API_RATELIMIT_PREF, "5")!!.toInt()
    )
    private val imageCDNRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        HttpUrl.parse(imageCDNUrl)!!,
        preferences.getString(IMAGE_CDN_RATELIMIT_PREF, "5")!!.toInt()
    )

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(apiRateLimitInterceptor)
        .addNetworkInterceptor(imageCDNRateLimitInterceptor)
        .build()

    private fun myGet(url: String) = GET(url)
        .newBuilder()
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/88.0.4324.93 " +
                "Mobile Safari/537.36 " +
                "Tachiyomi/1.0"
        )
        .build()!!

    // for simple searches (query only, no filters)
    private fun simpleSearchJsonParse(json: String): MangasPage {
        val arr = JSONArray(json)
        val ret = ArrayList<SManga>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cid = obj.getString("id")
            ret.add(
                SManga.create().apply {
                    title = obj.getString("comic_name")
                    thumbnail_url = cleanUrl(obj.getString("comic_cover"))
                    author = obj.optString("comic_author")
                    url = "/comic/comic_$cid.json?version=2.7.019"
                }
            )
        }
        return MangasPage(ret, false)
    }

    // for popular, latest, and filtered search
    private fun mangaFromJSON(json: String): MangasPage {
        val arr = JSONArray(json)
        val ret = ArrayList<SManga>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cid = obj.getString("id")
            ret.add(
                SManga.create().apply {
                    title = obj.getString("title")
                    thumbnail_url = obj.getString("cover")
                    author = obj.optString("authors")
                    status = when (obj.getString("status")) {
                        "已完结" -> SManga.COMPLETED
                        "连载中" -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                    url = "/comic/comic_$cid.json?version=2.7.019"
                }
            )
        }
        return MangasPage(ret, arr.length() != 0)
    }

    override fun popularMangaRequest(page: Int) = myGet("$apiUrl/classify/0/0/${page - 1}.json")

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = myGet("$apiUrl/classify/0/1/${page - 1}.json")

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query != "") {
            val uri = Uri.parse("http://s.acg.dmzj1.com/comicsum/search.php").buildUpon()
            uri.appendQueryParameter("s", query)
            return myGet(uri.toString())
        } else {
            var params = filters.map {
                if (it !is SortFilter && it is UriPartFilter) {
                    it.toUriPart()
                } else ""
            }.filter { it != "" }.joinToString("-")
            if (params == "") {
                params = "0"
            }

            val order = filters.filterIsInstance<SortFilter>().joinToString("") { (it as UriPartFilter).toUriPart() }

            return myGet("$apiUrl/classify/$params/$order/${page - 1}.json")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body()!!.string()

        return if (body.contains("g_search_data")) {
            simpleSearchJsonParse(body.substringAfter("=").trim().removeSuffix(";"))
        } else {
            mangaFromJSON(body)
        }
    }

    // Bypass mangaDetailsRequest, fetch v3api url directly
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(GET(apiUrl + manga.url, headers))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private val re1 = Regex("""\d+""") // Get comic ID from manga.url
    // Workaround to allow "Open in browser" use human readable webpage url.
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/info/${re1.find(manga.url)!!.value}.html")
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(apiUrl + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val obj = JSONObject(response.body()!!.string())

        title = obj.getString("title")
        thumbnail_url = obj.getString("cover")

        var arr = obj.getJSONArray("authors")
        val tmparr = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            tmparr.add(arr.getJSONObject(i).getString("tag_name"))
        }
        author = tmparr.joinToString(", ")

        arr = obj.getJSONArray("types")
        tmparr.clear()
        for (i in 0 until arr.length()) {
            tmparr.add(arr.getJSONObject(i).getString("tag_name"))
        }
        genre = tmparr.joinToString(", ")
        status = when (obj.getJSONArray("status").getJSONObject(0).getInt("tag_id")) {
            2310 -> SManga.COMPLETED
            2309 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        description = obj.getString("description")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val obj = JSONObject(response.body()!!.string())
        val ret = ArrayList<SChapter>()
        val cid = obj.getString("id")
        val arr = obj.getJSONArray("chapters")
        for (i in 0 until arr.length()) {
            val obj2 = arr.getJSONObject(i)
            val arr2 = obj2.getJSONArray("data")
            val prefix = obj2.getString("title")
            for (j in 0 until arr2.length()) {
                val chapter = arr2.getJSONObject(j)
                ret.add(
                    SChapter.create().apply {
                        name = "$prefix: ${chapter.getString("chapter_title")}"
                        date_upload = chapter.getString("updatetime").toLong() * 1000 // milliseconds
                        url = "https://api.m.dmzj1.com/comic/chapter/$cid/${chapter.getString("chapter_id")}.html"
                    }
                )
            }
        }
        return ret
    }

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers) // Bypass base url

    override fun pageListParse(response: Response): List<Page> {
        // some chapters are hidden and won't return a JSONObject from api.m.dmzj, have to get them through v3api (but images won't be as HQ)
        val arr = try {
            val obj = JSONObject(response.body()!!.string())
            obj.getJSONObject("chapter").getJSONArray("page_url") // api.m.dmzj1.com already return HD image url
        } catch (_: Exception) {
            // example url: http://v3api.dmzj.com/chapter/44253/101852.json
            val url = response.request().url().toString()
                .replace("api.m", "v3api")
                .replace("comic/", "")
                .replace(".html", ".json")
            val obj = client.newCall(GET(url, headers)).execute().let { JSONObject(it.body()!!.string()) }
            obj.getJSONArray("page_url_hd") // page_url in v3api.dmzj1.com will return compressed image, page_url_hd will return HD image url as api.m.dmzj1.com does.
        }
        val ret = ArrayList<Page>(arr.length())
        for (i in 0 until arr.length()) {
            ret.add(Page(i, "", arr.getString(i).replace("http:", "https:")))
        }
        return ret
    }

    private fun String.encoded(): String {
        return this.chunked(1)
            .joinToString("") { if (it in setOf("%", " ", "+", "#")) URLEncoder.encode(it, "UTF-8") else it }
            .let { if (it.endsWith(".jp")) "${it}g" else it }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!.encoded(), headers)
    }

    // Unused, we can get image urls directly from the chapter page
    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreGroup(),
        StatusFilter(),
        TypeFilter(),
        ReaderFilter()
    )

    private class GenreGroup : UriPartFilter(
        "分类",
        arrayOf(
            Pair("全部", ""),
            Pair("冒险", "4"),
            Pair("百合", "3243"),
            Pair("生活", "3242"),
            Pair("四格", "17"),
            Pair("伪娘", "3244"),
            Pair("悬疑", "3245"),
            Pair("后宫", "3249"),
            Pair("热血", "3248"),
            Pair("耽美", "3246"),
            Pair("其他", "16"),
            Pair("恐怖", "14"),
            Pair("科幻", "7"),
            Pair("格斗", "6"),
            Pair("欢乐向", "5"),
            Pair("爱情", "8"),
            Pair("侦探", "9"),
            Pair("校园", "13"),
            Pair("神鬼", "12"),
            Pair("魔法", "11"),
            Pair("竞技", "10"),
            Pair("历史", "3250"),
            Pair("战争", "3251"),
            Pair("魔幻", "5806"),
            Pair("扶她", "5345"),
            Pair("东方", "5077"),
            Pair("奇幻", "5848"),
            Pair("轻小说", "6316"),
            Pair("仙侠", "7900"),
            Pair("搞笑", "7568"),
            Pair("颜艺", "6437"),
            Pair("性转换", "4518"),
            Pair("高清单行", "4459"),
            Pair("治愈", "3254"),
            Pair("宅系", "3253"),
            Pair("萌系", "3252"),
            Pair("励志", "3255"),
            Pair("节操", "6219"),
            Pair("职场", "3328"),
            Pair("西方魔幻", "3365"),
            Pair("音乐舞蹈", "3326"),
            Pair("机战", "3325")
        )
    )

    private class StatusFilter : UriPartFilter(
        "连载状态",
        arrayOf(
            Pair("全部", ""),
            Pair("连载", "2309"),
            Pair("完结", "2310")
        )
    )

    private class TypeFilter : UriPartFilter(
        "地区",
        arrayOf(
            Pair("全部", ""),
            Pair("日本", "2304"),
            Pair("韩国", "2305"),
            Pair("欧美", "2306"),
            Pair("港台", "2307"),
            Pair("内地", "2308"),
            Pair("其他", "8453")
        )
    )

    private class SortFilter : UriPartFilter(
        "排序",
        arrayOf(
            Pair("人气", "0"),
            Pair("更新", "1")
        )
    )

    private class ReaderFilter : UriPartFilter(
        "读者",
        arrayOf(
            Pair("全部", ""),
            Pair("少年", "3262"),
            Pair("少女", "3263"),
            Pair("青年", "3264")
        )
    )

    // Headers
    override fun headersBuilder() =
        super.headersBuilder().add("Referer", "https://www.dmzj1.com/")!!

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val apiRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = API_RATELIMIT_PREF
            title = API_RATELIMIT_PREF_TITLE
            summary = API_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue("5")
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(API_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val imgCDNRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue("5")
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(IMAGE_CDN_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(apiRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val apiRateLimitPreference = ListPreference(screen.context).apply {
            key = API_RATELIMIT_PREF
            title = API_RATELIMIT_PREF_TITLE
            summary = API_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue("5")
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(API_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val imgCDNRateLimitPreference = ListPreference(screen.context).apply {
            key = IMAGE_CDN_RATELIMIT_PREF
            title = IMAGE_CDN_RATELIMIT_PREF_TITLE
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY

            setDefaultValue("5")
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(IMAGE_CDN_RATELIMIT_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
        screen.addPreference(apiRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
    }

    companion object {
        private const val API_RATELIMIT_PREF = "apiRatelimitPreference"
        private const val API_RATELIMIT_PREF_TITLE = "主站每秒连接数限制" // "Ratelimit permits per second for main website"
        private const val API_RATELIMIT_PREF_SUMMARY = "此值影响向动漫之家网站发起连接请求的数量。调低此值可能减少发生HTTP 429（连接请求过多）错误的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s" // "This value affects network request amount to dmzj's url. Lower this value may reduce the chance to get HTTP 429 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "图片CDN每秒连接数限制" // "Ratelimit permits per second for image CDN"
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "此值影响加载图片时发起连接请求的数量。调低此值可能减小图片加载错误的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s" // "This value affects network request amount for loading image. Lower this value may reduce the chance to get error when loading image, but loading speed will be slower too. Tachiyomi restart required. Current value: %s"

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()
    }
}
