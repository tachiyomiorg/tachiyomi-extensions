package eu.kanade.tachiyomi.extension.zh.mangabz

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.lib.ratelimit.SpecificHostRateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.ArrayList

class Mangabz : ConfigurableSource, HttpSource() {

    override val lang = "zh"
    override val supportsLatest = false
    override val name = "Mangabz"
    override val baseUrl = "https://mangabz.com"
    private val imageServer = arrayOf("https://cover.mangabz.com", "https://image.mangabz.com")

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val mainSiteRateLimitInterceptor = SpecificHostRateLimitInterceptor(HttpUrl.parse(baseUrl)!!, preferences.getString(MAINSITE_RATELIMIT_PREF, "5")!!.toInt())
    private val imageCDNRateLimitInterceptor1 = SpecificHostRateLimitInterceptor(HttpUrl.parse(imageServer[0])!!, preferences.getString(IMAGE_CDN_RATELIMIT_PREF, "5")!!.toInt())
    private val imageCDNRateLimitInterceptor2 = SpecificHostRateLimitInterceptor(HttpUrl.parse(imageServer[1])!!, preferences.getString(IMAGE_CDN_RATELIMIT_PREF, "5")!!.toInt())

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "https://mangabz.com")
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.104 Safari/537.36")

    private val showZhHantWebsite = preferences.getBoolean(SHOW_ZH_HANT_WEBSITE_PREF, false)

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(mainSiteRateLimitInterceptor)
        .addNetworkInterceptor(imageCDNRateLimitInterceptor1)
        .addNetworkInterceptor(imageCDNRateLimitInterceptor2)
        .addNetworkInterceptor { chain ->
            val cookies = chain.request().header("Cookie")?.replace(replaceCookiesRegex, "") ?: ""
            val newReq = chain
                .request()
                .newBuilder()
                .header("Cookie", if (showZhHantWebsite) cookies else "$cookies; mangabz_lang=2")
                .build()
            chain.proceed(newReq)
        }.build()!!

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangasList = ArrayList<SManga>(0)

        // top banner
        document.select("div.banner-con a").map { element ->
            mangasList.add(
                SManga.create().apply {
                    title = element.attr("title")
                    url = element.attr("href")
                    thumbnail_url = element.select("img").first().attr("src")
                }
            )
        }

        // ranking sidebar
        document.select(".rank-list .list").map { element ->
            mangasList.add(
                SManga.create().apply {
                    title = element.select(".rank-item-title").first().text()
                    url = element.select("a").first().attr("href")
                    thumbnail_url = element.select("a img").first().attr("src")
                }
            )
        }

        // carousel list
        document.select(".carousel-right-item").map { element ->
            mangasList.add(
                SManga.create().apply {
                    title = element.select(".carousel-right-item-title a").first().text()
                    url = element.select(".carousel-right-item-title a").first().attr("href")
                    thumbnail_url = element.select("a img").first().attr("src")
                }
            )
        }

        // recommend list
        document.select(".index-manga-item").map { element ->
            mangasList.add(
                SManga.create().apply {
                    title = element.select(".index-manga-item-title").first().text()
                    url = element.select(".index-manga-item-title a").first().attr("href")
                    thumbnail_url = element.select("a img").first().attr("src")
                }
            )
        }

        return MangasPage(mangasList.distinctBy { it.url }, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH) && query.contains(extractMangaIdRegex)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(GET("$baseUrl/$id", headers))
                .asObservableSuccess()
                .map { response ->
                    val sManga = mangaDetailsParse(response)
                    sManga.url = "/$id"
                    return@map MangasPage(listOf(sManga), false)
                }
        } else if (query.startsWith(baseUrl) && query.contains(extractMangaIdRegex)) {
            val id = extractMangaIdRegex.find(query)?.value
            client.newCall(GET("$baseUrl/$id", headers))
                .asObservableSuccess()
                .map { response ->
                    val sManga = mangaDetailsParse(response)
                    sManga.url = "/$id"
                    return@map MangasPage(listOf(sManga), false)
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?title=$query&page=$page")

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mh-list .mh-item").map { element ->
            SManga.create().apply {
                title = element.select(".mh-item-detali h2.title a").first().text()
                url = element.select(".mh-item-detali h2.title a").first().attr("href")
                thumbnail_url = element.select("a img.mh-cover").first().attr("src")
            }
        }
        val hasNextPage = document.select(".page-pagination li:contains(>)").first() != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used.")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used.")

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers.newBuilder().set("Referer", baseUrl + manga.url).build())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".detail-info-title").first().text()
            thumbnail_url = document.select("img.detail-info-cover").first().attr("src")
            status = when (document.select("span:contains(状态)>span, span:contains(狀態)>span").first().text()) {
                "连载中" -> SManga.ONGOING
                "連載中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = document.select("span:contains(作者) a")?.first()?.text() ?: ""
            genre = document.select(".item")?.first()?.text() ?: ""
            description = document.select(".detail-info-content")?.first()?.text() ?: ""
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val latestChapter = document.select(".s a").first().attr("href")
        val chapterInfo = document.select(".detail-list-form-title").first().text()
        val latestUploadDate = parseDate(chapterInfo)

        return document.select("a.detail-list-form-item").map { element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.text()
                chapter_number = chapterNumRegex.find(name)?.value?.toFloatOrNull() ?: -1F
                if (url == latestChapter) {
                    date_upload = latestUploadDate
                }
            }
        }
    }

    private fun parseDate(string: String): Long {
        val rightNow = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
        // today
        if (string.contains("今天")) {
            return rightNow.toInstant().toEpochMilli()
        }
        // yesterday
        if (string.contains("昨天")) {
            return rightNow.minusDays(1).toInstant().toEpochMilli()
        }
        // the day before yesterday
        if (string.contains("前天")) {
            return rightNow.minusDays(2).toInstant().toEpochMilli()
        }
        // 2021-01-01
        val result1 = dateRegex1.find(string)?.value
        if (result1 != null) {
            return LocalDate.parse("$result1").atTime(0, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        }
        // 1月1号 or 1月1號 -> (1, 1)
        val result2 = dateRegex2.find(string)?.groupValues
        if (result2 != null && result2.size > 1) {
            val d = rightNow.withMonth(result2[1].toInt()).withDayOfMonth(result2[2].toInt())
            return d.toInstant().toEpochMilli()
        }
        return rightNow.toInstant().toEpochMilli()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers.newBuilder().set("Referer", baseUrl + chapter.url).build())
    }

    // Special thanks to Cimoc project.
    // https://github.com/feilongfl/Cimoc/blob/03d378ddb5fe8684ef85cae673624afdb68fcf46/app/src/main/java/com/hiroshi/cimoc/source/MangaBZ.kt#L95
    private fun getJSVar(html: String, keyword: String, searchFor: String): String? {
        val re = Regex("var\\s+$keyword\\s*=\\s*$searchFor\\s*;")
        val match = re.find(html)
        return match?.groups?.get(1)?.value
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptTag = (document.select("head script").filter { it.data().isNotBlank() })[0].data()
        val chapterUrl = response.request().url().toString()

        val mid = getJSVar(scriptTag, "MANGABZ_MID", "(\\w+)")!!
        val cid = getJSVar(scriptTag, "MANGABZ_CID", "(\\w+)")!!
        val sign = getJSVar(scriptTag, "MANGABZ_VIEWSIGN", """\"(\w+)\"""")!!
        val pageCount = getJSVar(scriptTag, "MANGABZ_IMAGE_COUNT", "(\\d+)")!!.toInt()
        val path = getJSVar(scriptTag, "MANGABZ_CURL", "\"/(\\w+)/\"")!!

        // Page list return by webpage's API maybe incomplete, so we store API url and
        // chapter url in page.url to build header and fetch API when needed.
        val pagesList = MutableList(
            pageCount,
            init = { index ->
                Page(
                    index,
                    url = "$baseUrl/$path/chapterimage.ashx?cid=$cid&page=${index + 1}&key=&_cid=$cid&_mid=$mid&_sign=$sign&_dt=\n" +
                        chapterUrl
                )
            }
        ) // Fill the list at first.

        // Page 1 may return 1~2 image urls.
        val apiUrlInPage1 = "$baseUrl/$path/chapterimage.ashx?cid=$cid&page=1&key=&_cid=$cid&_mid=$mid&_sign=$sign&_dt="
        val imgUrlList = fetchImageUrlListFromAPI(apiUrlInPage1, response.request().headers())
        for (i in 0 until imgUrlList.length()) {
            val imgUrl = imgUrlList[i] as String
            val pageNum = extractPageNumFromImageUrlRegex.find(imgUrl)!!.groups[1]!!.value.toInt() - 1
            pagesList[pageNum] = Page(pageNum, "$apiUrlInPage1\n$chapterUrl", imgUrl)
        }

        return pagesList
    }

    private fun fetchImageUrlListFromAPI(apiUrl: String, requestHeaders: Headers = headers): JSONArray {
        val jsEvalPayload = client.newCall(GET(apiUrl, requestHeaders)).execute().body()!!.string()
        val imgUrlDecode = Duktape.create().use {
            it.evaluate("$jsEvalPayload; JSON.stringify(d);") as String
        }
        return JSONArray(imgUrlDecode)
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        if (page.imageUrl != null) {
            return Observable.just(page.imageUrl)
        } else {
            val urls = page.url.split("\n")
            val imgUrlList = fetchImageUrlListFromAPI(urls[0], headers.newBuilder().set("Referer", urls[1]).build())

            for (i in 0 until imgUrlList.length()) {
                val imgUrl = imgUrlList[i] as String
                val pageNum = extractPageNumFromImageUrlRegex.find(imgUrl)!!.groups[1]!!.value.toInt() - 1
                if (page.index == pageNum) {
                    return Observable.just(imgUrl)
                }
            }
            return Observable.error(Exception("Can't find image urls"))
        }
    }

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val mainSiteRateLimitPreference = androidx.preference.ListPreference(screen.context).apply {
            key = MAINSITE_RATELIMIT_PREF
            title = MAINSITE_RATELIMIT_PREF_TITLE
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = MAINSITE_RATELIMIT_PREF_SUMMARY

            setDefaultValue("5")
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATELIMIT_PREF, newValue as String).commit()
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
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY

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

        val zhHantPreference = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SHOW_ZH_HANT_WEBSITE_PREF
            title = SHOW_ZH_HANT_WEBSITE_PREF_TITLE
            summary = SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY

            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putBoolean(SHOW_ZH_HANT_WEBSITE_PREF, newValue as Boolean).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(mainSiteRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
        screen.addPreference(zhHantPreference)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mainSiteRateLimitPreference = ListPreference(screen.context).apply {
            key = MAINSITE_RATELIMIT_PREF
            title = MAINSITE_RATELIMIT_PREF_TITLE
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = MAINSITE_RATELIMIT_PREF_SUMMARY

            setDefaultValue("5")
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATELIMIT_PREF, newValue as String).commit()
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
            entries = ENTRIES_ARRAY
            entryValues = ENTRIES_ARRAY
            summary = IMAGE_CDN_RATELIMIT_PREF_SUMMARY

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

        val zhHantPreference = CheckBoxPreference(screen.context).apply {
            key = SHOW_ZH_HANT_WEBSITE_PREF
            title = SHOW_ZH_HANT_WEBSITE_PREF_TITLE
            summary = SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY

            setDefaultValue(false)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putBoolean(SHOW_ZH_HANT_WEBSITE_PREF, newValue as Boolean).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(mainSiteRateLimitPreference)
        screen.addPreference(imgCDNRateLimitPreference)
        screen.addPreference(zhHantPreference)
    }

    companion object {
        private const val MAINSITE_RATELIMIT_PREF = "mainSiteRatelimitPreference"
        private const val MAINSITE_RATELIMIT_PREF_TITLE = "主站每秒连接数限制" // "Ratelimit permits per second for main website"
        private const val MAINSITE_RATELIMIT_PREF_SUMMARY = "此值影响向网站发起连接请求的数量。调低此值可能减少发生HTTP 429（连接请求过多）错误的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s" // "This value affects network request amount to main website url. Lower this value may reduce the chance to get HTTP 429 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s"

        private const val IMAGE_CDN_RATELIMIT_PREF = "imgCDNRatelimitPreference"
        private const val IMAGE_CDN_RATELIMIT_PREF_TITLE = "图片CDN每秒连接数限制" // "Ratelimit permits per second for image CDN"
        private const val IMAGE_CDN_RATELIMIT_PREF_SUMMARY = "此值影响加载图片时发起连接请求的数量。调低此值可能减小IP被屏蔽的几率，但加载速度也会变慢。需要重启软件以生效。\n当前值：%s" // "This value affects network request amount for loading image. Lower this value may reduce the chance to get IP Ban, but loading speed will be slower too. Tachiyomi restart required."

        private const val SHOW_ZH_HANT_WEBSITE_PREF = "showZhHantWebsite"
        private const val SHOW_ZH_HANT_WEBSITE_PREF_TITLE = "使用繁体版网站" // "Use traditional chinese version website"
        private const val SHOW_ZH_HANT_WEBSITE_PREF_SUMMARY = "需要重启软件以生效。" // "You need to restart Tachiyomi"

        private val replaceCookiesRegex = Regex("""mangabz_lang=\d[;\s]*""")
        private val extractMangaIdRegex = Regex("""\d+bz""")
        private val chapterNumRegex = Regex("""\d+""")
        private val dateRegex1 = Regex("""\d{4}-\d{1,2}-\d{1,2}""")
        private val dateRegex2 = Regex("""(\d{1,2})月(\d{1,2})[号號]?""")
        private val extractPageNumFromImageUrlRegex = Regex("""/(\d+)_\d+\.""")

        private val ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()
        const val PREFIX_ID_SEARCH = "id:"
    }
}
