package eu.kanade.tachiyomi.extension.zh.manhuagui

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.PreferenceScreen
import com.google.gson.Gson
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Manhuagui : ConfigurableSource, ParsedHttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val name = "漫画柜"
    override val baseUrl =
            if (preferences.getBoolean(SHOW_ZH_HANT_WEBSITE_PREF, false))
                "https://tw.manhuagui.com"
            else
                "https://www.manhuagui.com"
    override val lang = "zh"
    override val supportsLatest = true

    private val imageServer = arrayOf("https://i.hamreus.com")
    private val gson = Gson()
    private val baseHttpUrl: HttpUrl = HttpUrl.parse(baseUrl)!!

    // Add rate limit to fix manga thumbnail load failure
    private val rateLimitInterceptor = RateLimitInterceptor(5, 1, TimeUnit.SECONDS)

    override val client: OkHttpClient =
            if (getShowR18())
                network.client.newBuilder()
                        .addNetworkInterceptor(rateLimitInterceptor)
                        .addNetworkInterceptor(AddCookieHeaderInterceptor(baseHttpUrl))
                        .build()
            else
                network.client.newBuilder()
                        .addNetworkInterceptor(rateLimitInterceptor)
                        .build()

    // Add R18 verification cookie
    class AddCookieHeaderInterceptor(private val baseHttpUrl: HttpUrl) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (chain.request().url().host() == baseHttpUrl.host()) {
                val originalCookies = chain.request().header("Cookie") ?: ""
                if (originalCookies != "") {
                    return chain.proceed(chain.request().newBuilder()
                            .header("Cookie", "$originalCookies; isAdult=1")
                            .build()
                    )
                }
            }
            return chain.proceed(chain.request())
        }
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/list/view_p$page.html", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/list/update_p$page.html", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
            GET("$baseUrl/s/${query}_p$page.html", headers)

    override fun mangaDetailsRequest(manga: SManga): Request {
        var bid = Regex("""\d+/?$""").find(manga.url)?.value
        if (bid != null) {
            bid = bid.removeSuffix("/")

            // Send a get request to https://www.manhuagui.com/tools/vote.ashx?act=get&bid=$bid
            // and a post request to https://www.manhuagui.com/tools/submit_ajax.ashx?action=user_check_login
            // to simulate what web page javascript do and get "country" cookie.
            // Send requests using coroutine in another (IO) thread.
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    // Delay 1 second to wait main manga details request complete
                    delay(1000L)
                    client.newCall(POST("$baseUrl/tools/submit_ajax.ashx?action=user_check_login", headersBuilder()
                            .set("Referer", manga.url)
                            .set("X-Requested-With", "XMLHttpRequest")
                            .build()
                    )).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) = e.printStackTrace()
                        override fun onResponse(call: Call, response: Response) = response.close()
                    })

                    client.newCall(GET("$baseUrl/tools/vote.ashx?act=get&bid=$bid", headersBuilder()
                            .set("Referer", manga.url)
                            .set("X-Requested-With", "XMLHttpRequest").build()
                    )).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) = e.printStackTrace()
                        override fun onResponse(call: Call, response: Response) = response.close()
                    })
                }
            }
        }

        return GET(baseUrl + manga.url, headers)
    }

    override fun popularMangaSelector() = "ul#contList > li"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = "div.book-result > ul > li"
    override fun chapterListSelector() = "ul > li > a.status0"

    override fun searchMangaNextPageSelector() = "span.current + a" // "a.prev" contain 2~4 elements: first, previous, next and last page, "span.current + a" is a better choice.
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
            .set("Referer", baseUrl)
            .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; ) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4086.0 Safari/537.36")

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.bcover").first().let {
            manga.url = it.attr("href")
            manga.title = it.attr("title").trim()

            // Fix thumbnail lazy load
            val thumbnailElement = it.select("img").first()
            manga.thumbnail_url = if (thumbnailElement.hasAttr("src"))
                thumbnailElement.attr("abs:src")
            else
                thumbnailElement.attr("abs:data-src")
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("div.book-detail").first().let {
            manga.url = it.select("dl > dt > a").first().attr("href")
            manga.title = it.select("dl > dt > a").first().attr("title").trim()
            manga.thumbnail_url = element.select("div.book-cover > a.bcover > img").first().attr("abs:src")
        }

        return manga
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not used")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        // Try to get R18 manga hidden chapter list
        val hiddenEncryptedChapterList = document.select("#__VIEWSTATE").first()
        if (hiddenEncryptedChapterList != null) {
            if (getShowR18()) {
                // Hidden chapter list is LZString encoded
                val decodedHiddenChapterList = Duktape.create().use {
                    it.evaluate(jsDecodeFunc +
                            """LZString.decompressFromBase64('${hiddenEncryptedChapterList.`val`()}');""") as String
                }
                val hiddenChapterList = Jsoup.parse(decodedHiddenChapterList, response.request().url().toString())
                if (hiddenChapterList != null) {
                    // Replace R18 warning with actual chapter list
                    document.select("#erroraudit_show").first().replaceWith(hiddenChapterList)
                    // Remove hidden chapter list element
                    document.select("#__VIEWSTATE").first().remove()
                }
            } else {
                // "You need to enable R18 switch and restart Tachiyomi to read this manga"
                error("您需要打开R18作品显示开关并重启软件才能阅读此作品")
            }
        }
        val chapterList = document.select("ul > li > a.status0")
        val latestChapterHref = document.select("div.book-detail > ul.detail-list > li.status > span > a.blue").first()?.attr("href")
        val chNumRegex = Regex("""\d+""")
        chapterList.forEach {
            val currentChapter = SChapter.create()
            currentChapter.url = it.attr("href")
            currentChapter.name = it?.attr("title")?.trim() ?: it.select("span").first().ownText()
            currentChapter.chapter_number = chNumRegex.find(currentChapter.name)?.value?.toFloatOrNull() ?: 0F

            // Manhuagui only provide upload date for latest chapter
            if (currentChapter.url == latestChapterHref) {
                currentChapter.date_upload = parseDate(document.select("div.book-detail > ul.detail-list > li.status > span > span.red").last())
            }
            chapters.add(currentChapter)
        }

        return chapters.sortedByDescending { it.chapter_number }
    }

    private fun parseDate(element: Element): Long = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(element.text())?.time ?: 0

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.description = document.select("div#intro-all").text().trim()
        manga.thumbnail_url = document.select("p.hcover > img").attr("abs:src")
        manga.artist = document.select("span:contains(漫画作者) > a , span:contains(漫畫作者) > a").text().trim()
        manga.genre = document.select("span:contains(漫画剧情) > a , span:contains(漫畫劇情) > a").text().trim()
        manga.status = when (document.select("div.book-detail > ul.detail-list > li.status > span > span").first().text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        return manga
    }

    private val jsDecodeFunc = """
        var LZString=(function(){var f=String.fromCharCode;var keyStrBase64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";var baseReverseDic={};function getBaseValue(alphabet,character){if(!baseReverseDic[alphabet]){baseReverseDic[alphabet]={};for(var i=0;i<alphabet.length;i++){baseReverseDic[alphabet][alphabet.charAt(i)]=i}}return baseReverseDic[alphabet][character]}var LZString={decompressFromBase64:function(input){if(input==null)return"";if(input=="")return null;return LZString._0(input.length,32,function(index){return getBaseValue(keyStrBase64,input.charAt(index))})},_0:function(length,resetValue,getNextValue){var dictionary=[],next,enlargeIn=4,dictSize=4,numBits=3,entry="",result=[],i,w,bits,resb,maxpower,power,c,data={val:getNextValue(0),position:resetValue,index:1};for(i=0;i<3;i+=1){dictionary[i]=i}bits=0;maxpower=Math.pow(2,2);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}switch(next=bits){case 0:bits=0;maxpower=Math.pow(2,8);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}c=f(bits);break;case 1:bits=0;maxpower=Math.pow(2,16);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}c=f(bits);break;case 2:return""}dictionary[3]=c;w=c;result.push(c);while(true){if(data.index>length){return""}bits=0;maxpower=Math.pow(2,numBits);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}switch(c=bits){case 0:bits=0;maxpower=Math.pow(2,8);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}dictionary[dictSize++]=f(bits);c=dictSize-1;enlargeIn--;break;case 1:bits=0;maxpower=Math.pow(2,16);power=1;while(power!=maxpower){resb=data.val&data.position;data.position>>=1;if(data.position==0){data.position=resetValue;data.val=getNextValue(data.index++)}bits|=(resb>0?1:0)*power;power<<=1}dictionary[dictSize++]=f(bits);c=dictSize-1;enlargeIn--;break;case 2:return result.join('')}if(enlargeIn==0){enlargeIn=Math.pow(2,numBits);numBits++}if(dictionary[c]){entry=dictionary[c]}else{if(c===dictSize){entry=w+w.charAt(0)}else{return null}}result.push(entry);dictionary[dictSize++]=w+entry.charAt(0);enlargeIn--;w=entry;if(enlargeIn==0){enlargeIn=Math.pow(2,numBits);numBits++}}}};return LZString})();String.prototype.splic=function(f){return LZString.decompressFromBase64(this).split(f)};
    """

    // Page list is javascript eval encoded and LZString encoded, these website:
    // http://www.oicqzone.com/tool/eval/ , https://www.w3xue.com/tools/jseval/ ,
    // https://www.w3cschool.cn/tools/index?name=evalencode can try to decode javascript eval encoded content,
    // jsDecodeFunc's LZString.decompressFromBase64() can decode LZString.
    override fun pageListParse(document: Document): List<Page> {
        // R18 warning element (#erroraudit_show) is remove by web page javascript, so here the warning element
        // will always exist if this manga is R18 limited whether R18 verification cookies has been sent or not.
        // But it will not interfere parse mechanism below.
        if (document.select("#erroraudit_show").first() != null && !getShowR18())
            error("R18作品显示开关未开启或未生效") // "R18 setting didn't enabled or became effective"

        val html = document.html()
        val re = Regex("""window\[".*?"](\(.*\)\s*\{[\s\S]+}\s*\(.*\))""")
        val imgCode = re.find(html)?.groups?.get(1)?.value
        val imgDecode = Duktape.create().use {
            it.evaluate(jsDecodeFunc + imgCode) as String
        }

        val re2 = Regex("""\{.*}""")
        val imgJsonStr = re2.find(imgDecode)?.groups?.get(0)?.value
        val imageJson: Comic = gson.fromJson(imgJsonStr, Comic::class.java)

        return imageJson.files!!.mapIndexed { i, imgStr ->
            val imgurl = "${imageServer[0]}${imageJson.path}$imgStr?cid=${imageJson.cid}&md5=${imageJson.sl?.md5}"
            Page(i, "", imgurl)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        // Simplified/Traditional Chinese version website switch
        val zhHantPreference = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SHOW_ZH_HANT_WEBSITE_PREF
            // "Use traditional chinese version website"
            title = "使用繁体版网站"
            // "You need to restart Tachiyomi"
            summary = "需要重启软件。"

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

        // R18+ switch
        val r18Preference = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = SHOW_R18_PREF_Title
            // "R18 Setting"
            title = "R18作品显示设置"
            // "Please make sure your IP is not in Manhuagui's ban list, e.g., China mainland IP. Tachiyomi restart required.
            summary = "请确认您的IP不在漫画柜的屏蔽列表内，例如中国大陆IP。需要重启软件以生效。"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val newSetting = preferences.edit().putBoolean(SHOW_R18_PREF, newValue as Boolean).commit()
                    newSetting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(zhHantPreference)
        screen.addPreference(r18Preference)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val zhHantPreference = CheckBoxPreference(screen.context).apply {
            key = SHOW_ZH_HANT_WEBSITE_PREF
            title = "使用繁体版网站"
            summary = "需要重启软件。"

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

        val r18Preference = CheckBoxPreference(screen.context).apply {
            key = SHOW_R18_PREF_Title
            title = "R18作品显示设置"
            summary = "请确认您的IP不在漫画柜的屏蔽列表内，例如中国大陆IP。需要重启软件以生效。"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val newSetting = preferences.edit().putBoolean(SHOW_R18_PREF, newValue as Boolean).commit()
                    newSetting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(zhHantPreference)
        screen.addPreference(r18Preference)
    }

    private fun getShowR18(): Boolean = preferences.getBoolean(SHOW_R18_PREF, false)

    companion object {
        private const val SHOW_R18_PREF_Title = "R18Setting"
        private const val SHOW_R18_PREF = "showR18Default"
        private const val SHOW_ZH_HANT_WEBSITE_PREF = "showZhHantWebsite"
    }
}
