package eu.kanade.tachiyomi.extension.zh.dmzj

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.zh.dmzj.protobuf.ComicDetailResponse
import eu.kanade.tachiyomi.extension.zh.dmzj.utils.RSA
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
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Dmzj source
 */

class Dmzj : ConfigurableSource, HttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "动漫之家"
    override val baseUrl = "https://m.dmzj1.com"
    private val v3apiUrl = "https://v3api.dmzj1.com"
    private val v3ChapterApiUrl = "https://nnv3api.dmzj1.com"
    // v3api now shutdown the functionality to fetch manga detail and chapter list, so move these logic to v4api
    private val v4apiUrl = "https://nnv4api.dmzj1.com" // https://v4api.dmzj1.com
    private val apiUrl = "https://api.dmzj.com"
    private val oldPageListApiUrl = "https://api.m.dmzj1.com"
    private val webviewPageListApiUrl = "https://m.dmzj1.com/chapinfo"
    private val imageCDNUrl = "https://images.dmzj1.com"

    private fun cleanUrl(url: String) = if (url.startsWith("//"))
        "https:$url"
    else url

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val v3apiRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        v3apiUrl.toHttpUrlOrNull()!!,
        preferences.getString(API_RATELIMIT_PREF, "5")!!.toInt()
    )
    private val v4apiRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        v4apiUrl.toHttpUrlOrNull()!!,
        preferences.getString(API_RATELIMIT_PREF, "5")!!.toInt()
    )
    private val apiRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        apiUrl.toHttpUrlOrNull()!!,
        preferences.getString(API_RATELIMIT_PREF, "5")!!.toInt()
    )
    private val imageCDNRateLimitInterceptor = SpecificHostRateLimitInterceptor(
        imageCDNUrl.toHttpUrlOrNull()!!,
        preferences.getString(IMAGE_CDN_RATELIMIT_PREF, "5")!!.toInt()
    )

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(apiRateLimitInterceptor)
        .addNetworkInterceptor(v3apiRateLimitInterceptor)
        .addNetworkInterceptor(v4apiRateLimitInterceptor)
        .addNetworkInterceptor(imageCDNRateLimitInterceptor)
        .build()

    override fun headersBuilder() = Headers.Builder().apply {
        set("Referer", "https://www.dmzj1.com/")
        set(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/88.0.4324.93 " +
                "Mobile Safari/537.36 " +
                "Tachiyomi/1.0"
        )
    }

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

    private fun customUrlBuilder(baseUrl: String): HttpUrl.Builder {
        val rightNow = System.currentTimeMillis() / 1000
        return baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("channel", "android")
            .addQueryParameter("version", "3.0.0")
            .addQueryParameter("timestamp", rightNow.toInt().toString())
    }

    private fun decryptProtobufData(rawData: String): ByteArray {
        return RSA.decrypt(Base64.decode(rawData, Base64.DEFAULT), privateKey)
    }

    override fun popularMangaRequest(page: Int) = GET("$v3apiUrl/classify/0/0/${page - 1}.json")

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$v3apiUrl/classify/0/1/${page - 1}.json")

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    private fun searchMangaById(id: String): MangasPage {
        val comicNumberID = if (checkComicIdIsNumericalRegex.matches(id)) {
            id
        } else {
            // Chinese Pinyin ID
            val document = client.newCall(GET("$baseUrl/info/$id.html", headers)).execute().asJsoup()
            extractComicIdFromWebpageRegex.find(
                document.select("#Subscribe").attr("onclick")
            )!!.groups[1]!!.value // onclick="addSubscribe('{comicNumberID}')"
        }

        val sManga = try {
            val r = client.newCall(GET("$v4apiUrl/comic/detail/$comicNumberID.json", headers)).execute()
            mangaDetailsParse(r)
        } catch (_: Exception) {
            val r = client.newCall(GET("$apiUrl/dynamic/comicinfo/$comicNumberID.json", headers)).execute()
            mangaDetailsParse(r)
        }
        // Change url format to as same as mangaFromJSON, which used by popularMangaParse and latestUpdatesParse.
        // manga.url being used as key to identity a manga in tachiyomi, so if url format don't match popularMangaParse and latestUpdatesParse,
        // tachiyomi will mark them as unsubscribe in popularManga and latestUpdates page.
        sManga.url = "/comic/comic_$comicNumberID.json?version=2.7.019"

        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            // ID may be numbers or Chinese pinyin
            val id = query.removePrefix(PREFIX_ID_SEARCH).removeSuffix(".html")
            Observable.just(searchMangaById(id))
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query != "") {
            val uri = Uri.parse("http://s.acg.dmzj1.com/comicsum/search.php").buildUpon()
            uri.appendQueryParameter("s", query)
            return GET(uri.toString())
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

            return GET("$v3apiUrl/classify/$params/$order/${page - 1}.json")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()

        return if (body.contains("g_search_data")) {
            simpleSearchJsonParse(body.substringAfter("=").trim().removeSuffix(";"))
        } else {
            mangaFromJSON(body)
        }
    }

    // Bypass mangaDetailsRequest, fetch api url directly
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val cid = extractComicIdFromMangaUrlRegex.find(manga.url)!!.groups[1]!!.value
        return try {
            // Not using client.newCall().asObservableSuccess() to ensure we can catch exception here.
            val response = client.newCall(
                GET(
                    customUrlBuilder("$v4apiUrl/comic/detail/$cid").build().toString(), headers
                )
            ).execute()
            val sManga = mangaDetailsParse(response).apply { initialized = true }
            Observable.just(sManga)
        } catch (e: Exception) {
            val response = client.newCall(GET("$apiUrl/dynamic/comicinfo/$cid.json", headers)).execute()
            val sManga = mangaDetailsParse(response).apply { initialized = true }
            Observable.just(sManga)
        } catch (e: Exception) {
            Observable.error(e)
        }
    }

    // Workaround to allow "Open in browser" use human readable webpage url.
    override fun mangaDetailsRequest(manga: SManga): Request {
        val cid = extractComicIdFromMangaUrlRegex.find(manga.url)!!.groups[1]!!.value
        return GET("$baseUrl/info/$cid.html")
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val responseBody = response.body!!.string()
        if (response.request.url.toString().startsWith(v4apiUrl)) {
            val pb = ProtoBuf.decodeFromByteArray<ComicDetailResponse>(decryptProtobufData(responseBody))
            val pbData = pb.Data
            title = pbData.Title
            thumbnail_url = pbData.Cover
            author = pbData.Authors.joinToString(separator = ", ") { it.TagName }
            genre = pbData.TypesTypes.joinToString(separator = ", ") { it.TagName }

            status = when (pbData.Status[0].TagName) {
                "已完结" -> SManga.COMPLETED
                "连载中" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            description = pbData.Description
        } else {
            val obj = JSONObject(responseBody)
            val data = obj.getJSONObject("data").getJSONObject("info")
            title = data.getString("title")
            thumbnail_url = data.getString("cover")
            author = data.getString("authors")
            genre = data.getString("types").replace("/", ", ")
            status = when (data.getString("status")) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = data.getString("description")
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException("Not used.")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val cid = extractComicIdFromMangaUrlRegex.find(manga.url)!!.groups[1]!!.value
        return if (manga.status != SManga.LICENSED) {
            try {
                val response =
                    client.newCall(
                        GET(
                            customUrlBuilder("$v4apiUrl/comic/detail/$cid").build().toString(),
                            headers
                        )
                    ).execute()
                Observable.just(chapterListParse(response))
            } catch (e: Exception) {
                val response = client.newCall(GET("$apiUrl/dynamic/comicinfo/$cid.json", headers)).execute()
                Observable.just(chapterListParse(response))
            } catch (e: Exception) {
                Observable.error(e)
            }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val ret = ArrayList<SChapter>()
        val responseBody = response.body!!.string()
        if (response.request.url.toString().startsWith(v4apiUrl)) {
            val pb = ProtoBuf.decodeFromByteArray<ComicDetailResponse>(decryptProtobufData(responseBody))
            val mangaPBData = pb.Data
            val chapterPBData = mangaPBData.Chapters[0]
            for (i in chapterPBData.Data.indices) {
                val chapter = chapterPBData.Data[i]
                ret.add(
                    SChapter.create().apply {
                        name = chapter.ChapterTitle
                        date_upload = chapter.Updatetime * 1000
                        url = "${mangaPBData.Id}/${chapter.ChapterId}"
                    }
                )
            }
        } else {
            // get chapter info from old api
            val obj = JSONObject(responseBody)
            val chaptersList = obj.getJSONObject("data").getJSONArray("list")
            for (i in 0 until chaptersList.length()) {
                val chapter = chaptersList.getJSONObject(i)
                ret.add(
                    SChapter.create().apply {
                        name = chapter.getString("chapter_name")
                        date_upload = chapter.getString("updatetime").toLong() * 1000
                        url = "${chapter.getString("comic_id")}/${chapter.getString("id")}"
                    }
                )
            }
        }
        return ret
    }

    override fun pageListRequest(chapter: SChapter) = throw UnsupportedOperationException("Not used.")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return try {
            // webpage api
            val response = client.newCall(GET("$webviewPageListApiUrl/${chapter.url}.html", headers)).execute()
            Observable.just(pageListParse(response))
        } catch (e: Exception) {
            // api.m.dmzj1.com
            val response = client.newCall(GET("$oldPageListApiUrl/comic/chapter/${chapter.url}.html", headers)).execute()
            Observable.just(pageListParse(response))
        } catch (e: Exception) {
            // v3api
            val response = client.newCall(
                GET(
                    customUrlBuilder("$v3ChapterApiUrl/chapter/${chapter.url}.json").build().toString(),
                    headers
                )
            ).execute()
            Observable.just(pageListParse(response))
        } catch (e: Exception) {
            Observable.error(e)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val requestUrl = response.request.url.toString()
        val responseBody = response.body!!.string()
        val arr = if (
            requestUrl.startsWith(webviewPageListApiUrl) ||
            requestUrl.startsWith(v3ChapterApiUrl)
        ) {
            // webpage api or v3api
            JSONObject(responseBody).getJSONArray("page_url")
        } else if (requestUrl.startsWith(oldPageListApiUrl)) {
            try {
                val obj = JSONObject(responseBody)
                obj.getJSONObject("chapter").getJSONArray("page_url")
            } catch (e: org.json.JSONException) {
                // JSON data from api.m.dmzj1.com may be incomplete, extract page_url list using regex
                val extractPageList = extractPageListRegex.find(responseBody)!!.value
                JSONObject("{$extractPageList}").getJSONArray("page_url")
            }
        } else {
            throw Exception("can't parse response")
        }
        val ret = ArrayList<Page>(arr.length())
        for (i in 0 until arr.length()) {
            // Seems image urls from webpage api and api.m.dmzj1.com may be URL encoded multiple times
            val url = URLDecoder.decode(URLDecoder.decode(arr.getString(i), "UTF-8"), "UTF-8")
                .replace("http:", "https:")
                .replace("dmzj.com", "dmzj1.com")
            ret.add(Page(i, "", url))
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

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
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

        private val extractComicIdFromWebpageRegex = Regex("""addSubscribe\((\d+)\)""")
        private val checkComicIdIsNumericalRegex = Regex("""^\d+$""")
        private val extractComicIdFromMangaUrlRegex = Regex("""(\d+)\.(json|html)""") // Get comic ID from manga.url
        private val extractPageListRegex = Regex("""\"page_url\".+?\]""")

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()
        const val PREFIX_ID_SEARCH = "id:"

        private const val privateKey =
            "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAK8nNR1lTnIfIes6oRWJNj3mB6OssDGx0uGMpgpbVCpf6+VwnuI2stmhZNoQcM417Iz7WqlPzbUmu9R4dEKmLGEEqOhOdVaeh9Xk2IPPjqIu5TbkLZRxkY3dJM1htbz57d/roesJLkZXqssfG5EJauNc+RcABTfLb4IiFjSMlTsnAgMBAAECgYEAiz/pi2hKOJKlvcTL4jpHJGjn8+lL3wZX+LeAHkXDoTjHa47g0knYYQteCbv+YwMeAGupBWiLy5RyyhXFoGNKbbnvftMYK56hH+iqxjtDLnjSDKWnhcB7089sNKaEM9Ilil6uxWMrMMBH9v2PLdYsqMBHqPutKu/SigeGPeiB7VECQQDizVlNv67go99QAIv2n/ga4e0wLizVuaNBXE88AdOnaZ0LOTeniVEqvPtgUk63zbjl0P/pzQzyjitwe6HoCAIpAkEAxbOtnCm1uKEp5HsNaXEJTwE7WQf7PrLD4+BpGtNKkgja6f6F4ld4QZ2TQ6qvsCizSGJrjOpNdjVGJ7bgYMcczwJBALvJWPLmDi7ToFfGTB0EsNHZVKE66kZ/8Stx+ezueke4S556XplqOflQBjbnj2PigwBN/0afT+QZUOBOjWzoDJkCQClzo+oDQMvGVs9GEajS/32mJ3hiWQZrWvEzgzYRqSf3XVcEe7PaXSd8z3y3lACeeACsShqQoc8wGlaHXIJOHTcCQQCZw5127ZGs8ZDTSrogrH73Kw/HvX55wGAeirKYcv28eauveCG7iyFR0PFB/P/EDZnyb+ifvyEFlucPUI0+Y87F"
    }
}
