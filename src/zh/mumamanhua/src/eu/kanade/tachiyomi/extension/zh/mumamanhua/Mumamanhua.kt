package eu.kanade.tachiyomi.extension.zh.mumamanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mumamanhua : ParsedHttpSource() {

    override val baseUrl: String = "https://www.muamh.com"
    override val lang: String = "zh"
    override val name: String = "木马漫画"
    override val supportsLatest: Boolean = true

    // 识别手机浏览器请求头信息集合(User-Agent)
    //headers request (User-Agent)
    //这个网站只能使用手机中的浏览器进行访问,无法使用Windows浏览器访问,原因是该网站在headers request中对手机浏览器进行了识别
    //This website can only be accessed using the browser on the mobile phone, Cannot access using windows browser,because the website recognizes the mobile browser in the headers request
    //在多个标头请求中添加“ User-Agent”的值，以伪造移动浏览器访问权限。添加许多内容的原因是该网站具有反网站搜寻器(爬虫)
    //Add the value of "User-Agent" to multiple header requests to fake mobile browser access rights.The reason for adding a lot of content is that the website has a website anti-searcher
    private val phone = mapOf<Int, String>(
        1 to "Mozilla/5.0 (iPad; CPU OS 11_0 like Mac OS X) AppleWebKit/604.1.34 (KHTML, like Gecko) Version/11.0 Mobile/15A5341f Safari/604.1",
        2 to "Mozilla/5.0 (Linux; Android 9; V1838A Build/PKQ1.190302.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        3 to "Mozilla/5.0 (Linux; Android 7.1.1; OPPO R11 Plus Build/NMF26X; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/63.0.3239.83 Mobile Safari/537.36 T7/11.16 SP-engine/2.12.0 baiduboxapp/11.16.2.10 (Baidu; P1 7.1.1)",
        4 to "Mozilla/5.0 (Linux; U; Android 5.1; zh-cn; OPPO R9tm Build/LMY47I) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/10.1 Mobile Safari/537.36",
        5 to "Mozilla/5.0 (Linux; Android 9; BND-AL00 Build/HONORBND-AL00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/63.0.3239.83 Mobile Safari/537.36 T7/11.6 baiduboxapp/11.6.1.10 (Baidu; P1 9)",
        6 to "Mozilla/5.0 (Linux; Android 9; MI 6X Build/PKQ1.180904.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/6.2 TBS/045111 Mobile Safari/537.36 MMWEBID/5003 MicroMessenger/7.0.11.1600(0x27000B33) Process/tools NetType/WIFI Language/zh_CN ABI/arm64",
        7 to "Mozilla/5.0 (Linux; Android 9; CLT-TL00 Build/HUAWEICLT-TL00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        8 to "Mozilla/5.0 (Linux; Android 9; FIG-TL10 Build/HUAWEIFIG-TL10; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.18 SP-engine/2.14.0 baiduboxapp/11.18.0.12 (Baidu; P1 9)",
        9 to "Mozilla/5.0 (Linux; U; Android 8.1.0; zh-cn; vivo X20 Build/OPM1.171019.011) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/9.9 Mobile Safari/537.36",
        10 to "Mozilla/5.0 (iPhone; CPU iPhone OS 12_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/16B92 MicroMessenger/7.0.9(0x17000929) NetType/WIFI Language/zh_HK",
        11 to "Mozilla/5.0 (Linux; Android 9; MI 8 Lite Build/PKQ1.181007.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/67.0.3396.87 XWEB/1169 MMWEBSDK/191201 Mobile Safari/537.36 MMWEBID/673 MicroMessenger/7.0.11.1600(0x27000B33) Process/tools NetType/WIFI Language/zh_CN ABI/arm64",
        12 to "Mozilla/5.0 (Linux; Android 9; PCAM00 Build/PKQ1.190519.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        13 to "Mozilla/5.0 (iPhone; CPU iPhone OS 12_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/7.0.11(0x17000b21) NetType/WIFI Language/zh_CN",
        14 to "Mozilla/5.0 (Linux; U; Android 7.1.1; zh-cn; ONEPLUS A5000 Build/NMF26X) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/10.1 Mobile Safari/537.36",
        15 to "Mozilla/5.0 (Linux; Android 7.1.2; vivo X9i Build/N2G47H; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 7.1.2)",
        16 to "Mozilla/5.0 (Linux; Android 9; SEA-AL10 Build/HUAWEISEA-AL1001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        17 to "Mozilla/5.0 (Linux; Android 9; MIX 2 Build/PKQ1.190118.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        18 to "Mozilla/5.0 (Linux; Android 10; V1914A Build/QP1A.190711.020; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 10)",
        19 to "Mozilla/5.0 (Linux; U; Android 10; zh-cn; GM1910 Build/QKQ1.190716.003) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/10.1 Mobile Safari/537.36",
        20 to "Mozilla/5.0 (Linux; Android 10; GM1910 Build/QKQ1.190716.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/6.2 TBS/045120 Mobile Safari/537.36 V1_AND_SQ_8.2.7_1334_YYB_D QQ/8.2.7.4410 NetType/WIFI WebP/0.3.0 Pixel/1440 StatusBarHeight/128 SimpleUISwitch/0",
        21 to "Mozilla/5.0 (Linux; Android 9; V1838T Build/PKQ1.190302.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        22 to "Mozilla/5.0 (Linux; Android 9; PACT00 Build/PPR1.180610.011; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        23 to "Mozilla/5.0 (Linux; Android 8.1.0; Mi Note 3 Build/OPM1.171019.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 8.1.0)",
        24 to "Mozilla/5.0 (Linux; Android 10; LYA-TL00 Build/HUAWEILYA-TL00L; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.2.2 (Baidu; P1 10)",
        25 to "Mozilla/5.0 (Linux; Android 9; Mi9 Pro 5G Build/PKQ1.190714.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        26 to "(Linux; U; Android 8.0.0; zh-CN; VTR-AL00 Build/HUAWEIVTR-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/11.8.0.960 Mobile Safari/537.36",
        27 to "Mozilla/5.0 (Linux; U; Android 8.0.0; zh-CN; VTR-AL00 Build/HUAWEIVTR-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/11.8.0.960 Mobile Safari/537.36",
        28 to "Mozilla/5.0 (Linux; Android 8.0.0; VTR-AL00 Build/HUAWEIVTR-AL00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/6.2 TBS/045120 Mobile Safari/537.36 V1_AND_SQ_8.2.0_1296_YYB_D QQ/8.2.0.4310 NetType/WIFI WebP/0.3.0 Pixel/1080 StatusBarHeight/72 SimpleUISwitch/0",
        29 to "Mozilla/5.0 (Linux; Android 9; ART-AL00x Build/HUAWEIART-AL00x; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        30 to "Mozilla/5.0 (Linux; U; Android 9; zh-CN; MHA-TL00 Build/HUAWEIMHA-TL00) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.8.9.1069 Mobile Safari/537.36",
        31 to "Mozilla/5.0 (Linux; U; Android 9; zh-cn; MI 9 SE Build/PKQ1.181121.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser/11.8.12",
        32 to "Mozilla/5.0 (Linux; U; Android 10; zh-CN; SEA-AL10 Build/HUAWEISEA-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.5.6.1036 Mobile Safari/537.36",
        33 to "Mozilla/5.0 (Linux; U; Android 10; zh-cn; Redmi K20 Pro Build/QKQ1.190825.002) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser/11.8.12",
        34 to "Mozilla/5.0 (Linux; U; Android 9; zh-cn; Redmi Note 7 Build/PKQ1.180904.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser/11.5.12",
        35 to "Mozilla/5.0 (Linux; U; Android 9; zh-CN; COR-AL10 Build/HUAWEICOR-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.8.9.1069 Mobile Safari/537.36",
        36 to "Mozilla/5.0 (Linux; U; Android 9; zh-CN; PAR-AL00 Build/HUAWEIPAR-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.8.9.1069 Mobile Safari/537.36",
        37 to "Mozilla/5.0 (Linux; Android 9; vivo X21A Build/PKQ1.180819.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.89 Mobile Safari/537.36 T7/11.20 SP-engine/2.16.0 baiduboxapp/11.20.0.14 (Baidu; P1 9)",
        38 to "Mozilla/5.0 (Linux; U; Android 10; zh-cn; MI 9 Build/QKQ1.190825.002) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/10.1 Mobile Safari/537.36",
        39 to "Mozilla/5.0 (Linux; U; Android 1; zh-CN; MI 9S Build/PKQ1.180904.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.8.6.1066 Mobile Safari/537.36",
        40 to "Mozilla/5.0 (Linux; U; Android 9; zh-cn; V1809A Build/PKQ1.181030.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/10.1 Mobile Safari/537.36"
    )

    // 设置请求头为手机标识
    //自定义 header request,只为了让请求信息如同使用手机中的浏览器访问一样
    //Custom header request, just to make the request information like using the browser in the mobile phone to access
    //对于headers request中的"User-Agent",使用随机获取,原因是如果同一个设备多次且快速的访问网站,网站会识别出请求是非法的爬虫请求(website searcher),导致设备或者改设备下的网络IP无法访问该网站
    //For the "User-Agent" in the headers request, random acquisition is used. The reason is that if the same device visits the website multiple times and quickly, the website will recognize that the request is an illegal crawler request (website searcher), resulting in the device or the device The network IP cannot access the website
    private var header = Headers.of(mapOf(
        "Cache-Control" to "max-age=0",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
        "Accept-Encoding" to "",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "User-Agent" to phone.get((1..40).random()),
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Connection" to "keep-alive",
        "Host" to "www.muamh.com"
    ))

    // 点击量排序(人气)
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/booklist?page=$page", header)
    }

    override fun popularMangaNextPageSelector(): String? = "section > a:has(img.nextb)"
    override fun popularMangaSelector(): String = "ul.comic-sort li.item div.comic-item"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3.title").text()
        url = element.select("div.thumbnail a").first().attr("href")
        thumbnail_url = element.select("img").attr("data-src")
        author = "未知"
    }

    // 重写访问漫画详细信息的访问方式,以添加访问头
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl" + manga.url, header)
    }

    // 最新排序
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/update?page=$page", header)
    }

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // 查询信息
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query != "") {
            val url = HttpUrl.parse("$baseUrl/search?keyword=$query&page=$page")?.newBuilder()
            GET(url.toString(), header)
        } else {
            val params = filters.map {
                if (it is UriPartFilter) {
                    it.toUriPart()
                } else ""
            }.filter { it != "" }.joinToString("")
            val url = HttpUrl.parse("$baseUrl$params&page=$page")?.newBuilder()
            GET(url.toString(), header)
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // 漫画详情
    // url网址 , title标题 , artist艺术家 , author作者 , description描述 , genre类型 , thumbnail_url缩图网址 , initialized是否初始化
    // status状态 0未知,1连载,2完结,3领取牌照
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.name").text()
        thumbnail_url = document.select("div.comic-item div.thumbnail img.thumbnail").attr("data-src")
        author = document.select("span.author").text()
        artist = author
        genre = getMangaGenre(document)!!.trim().split(" ").joinToString(", ")
        status = getMangaStatus(document)
        description = document.select("div.swiper-slide.tab-item div.comic-detail p.content").text()
    }

    // 查询漫画状态
    private fun getMangaStatus(document: Document): Int {
        val statusText = document.select("span.hasread.ift-fire").text()
        return when {
            statusText.indexOf("连载") > 0 -> 1
            statusText.indexOf("完结") > 0 -> 2
            else -> 0
        }
    }

    // 获取漫画标签
    private fun getMangaGenre(document: Document): String? {
        var genre = ""
        var elements = document.select("ul.tags-box li.tags a.tags span")
        if (elements.size == 0) {
            return null
        }
        for (element in elements) {
            genre += "${element.text().trim()} "
        }
        return genre
    }

    // 重写访问漫画章节的访问方式,以添加访问头
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl" + manga.url, header)
    }

    // 漫画章节信息
    override fun chapterListSelector(): String = "div.mk-chapterlist-box div.bd ul.chapterlist li.item"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a span.ellipsis").text()
        url = element.select("a.chapterBtn").attr("href")
    }

    // 漫画章节排序方式(倒序asReversed,正序reversed)
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    // 重写章节进入查看图片页的访问方式,以添加请求头
    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl" + chapter.url, header)
    }

    // 漫画图片信息
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        var elements = document.select("div.view-main-1.readForm img")
        for (element in elements) {
            add(Page(size, "", element.select("img").attr("data-original").split("\\?")[0]))
        }
    }

    // 重写图片的访问方式,以添加请求头
    override fun imageUrlRequest(page: Page): Request {
        return GET(page.url, header)
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")

    // Filters
    // 按照类别信息进行检索

    override fun getFilterList() = FilterList(
        UpdateGroup(),
        CutGroup(),
        TagGroup()
    )

    private class UpdateGroup : UriPartFilter("按进度", arrayOf(
        Pair("全部", "/booklist?"),
        Pair("连载中", "/booklist?end=0"),
        Pair("已完结", "/booklist?end=1")
    ))

    private class CutGroup : UriPartFilter("删减类型", arrayOf(
        Pair("全部", ""),
        Pair("无删减", "&areas=1"),
        Pair("删减", "&areas=2")
    ))

    private class TagGroup : UriPartFilter("按剧情", arrayOf(
        Pair("全部", ""),
        Pair("耽美", "&tag=耽美"),
        Pair("恋爱", "&tag=恋爱"),
        Pair("校园", "&tag=校园"),
        Pair("动作", "&tag=动作"),
        Pair("冒险", "&tag=冒险"),
        Pair("恐怖", "&tag=恐怖"),
        Pair("BL", "&tag=BL"),
        Pair("搞笑", "&tag=搞笑"),
        Pair("古风", "&tag=古风"),
        Pair("剧情", "&tag=剧情"),
        Pair("出版日漫", "&tag=出版日漫"),
        Pair("其他", "&tag=其他")
    ))

    /**
     *创建选择过滤器的类。 下拉菜单中的每个条目都有一个名称和一个显示名称。
     *如果选择了一个条目，它将作为查询参数附加到URI的末尾。
     *如果将firstIsUnspecified设置为true，则如果选择了第一个条目，则URI不会附加任何内容。
     */
    // vals: <name, display>
    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }
}
