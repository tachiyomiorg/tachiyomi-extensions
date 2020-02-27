package eu.kanade.tachiyomi.extension.zh.gufengmh

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


class Gufengmh : ParsedHttpSource() {
    override val name: String = "古风漫画网"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://m.gufengmh8.com"

    //Popular


    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list/click/?page=$page", headers)
    }
    override fun popularMangaNextPageSelector(): String? = "li.next"
    override fun popularMangaSelector(): String = "li.list-comic"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.txtA").text()
        setUrlWithoutDomain(element.select("a.txtA").attr("abs:href"))
        thumbnail_url = element.select("mip-img").attr("abs:src")
    }

    //Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/list/update/?page=$page", headers)
    }
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    //Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
        if (query.isNotBlank()) {
            uri.appendPath("search")
                .appendEncodedPath("")
                .appendQueryParameter("keywords", query)
                .appendQueryParameter("page", page.toString())
        } else {
            uri.appendPath("list")
            val pathBuilder = Uri.Builder()
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(pathBuilder)
            }
            val filterPath = pathBuilder.toString().replace("/","-").removePrefix("-")
            uri.appendEncodedPath(filterPath)
                .appendEncodedPath("")
        }

        return GET(uri.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = "div.itemBox, li.list-comic"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.title, a.txtA").text()
        setUrlWithoutDomain(element.select("a.title, a.txtA").attr("abs:href"))
        thumbnail_url = element.select("mip-img").attr("abs:src")
    }

    //Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.title").text()
        thumbnail_url = document.select("div#Cover mip-img").attr("abs:src")
        author = document.select("dt:contains(作者) + dd").text()
        artist = author
        genre = document.select("dt:contains(类别) + dd").text()
        description = document.select("p.txtDesc").text()
    }

    //Chapters

    override fun chapterListSelector(): String = "div.list li"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.select("a").attr("href")
        name = element.select("span").text()
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    //Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val script = document.select("script:containsData(chapterImages )").html()
        val images = script.substringAfter("chapterImages = [\"").substringBefore("\"]").split("\",\"")
        val path = script.substringAfter("chapterPath = \"").substringBefore("\";")
        val server = script.substringAfter("pageImage = \"").substringBefore("/images/cover")
        images.forEach {
            add(Page(size,"","$server/$path/$it"))
        }
    }
    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")

    //Filters

    override fun getFilterList(): FilterList {
        val filterList = FilterList(
            Filter.Header("如果使用文本搜索"),
            Filter.Header("过滤器将被忽略"),
            typefilter(),
            regionfilter(),
            genrefilter(),
            letterfilter(),
            statusfilter()
        )
        return filterList
    }

    private class typefilter: UriSelectFilterPath("按类型","filtertype", arrayOf(
        Pair("","全部"),
        Pair("shaonian","少年漫画"),
        Pair("shaonv","少女漫画"),
        Pair("qingnian","青年漫画"),
        Pair("zhenrenmanhua","真人漫画")
    ))

    private class regionfilter: UriSelectFilterPath("按地区","filterregion", arrayOf(
        Pair("","全部"),
        Pair("ribenmanhua","日本漫画"),
        Pair("guochanmanhua","国产漫画"),
        Pair("gangtaimanhua","港台漫画"),
        Pair("oumeimanhua","欧美漫画"),
        Pair("hanguomanhua","韩国漫画")
        ))

    private class genrefilter: UriSelectFilterPath("按剧情","filtergenre", arrayOf(
        Pair("","全部"),
        Pair("maoxian","冒险"),
        Pair("mofa","魔法"),
        Pair("kehuan","科幻"),
        Pair("kongbu","恐怖"),
        Pair("lishi","历史"),
        Pair("jingji","竞技"),
        Pair("huanlexiang","欢乐向"),
        Pair("xifangmohuan","西方魔幻"),
        Pair("aiqing","爱情"),
        Pair("xuanyi","悬疑"),
        Pair("qihuan","奇幻"),
        Pair("qingxiaoshuo","轻小说"),
        Pair("sige","四格"),
        Pair("shengui","神鬼"),
        Pair("zhiyu","治愈"),
        Pair("xiaoyuan","校园"),
        Pair("weiniang","伪娘"),
        Pair("danmei","耽美"),
        Pair("hougong","后宫"),
        Pair("mohuan","魔幻"),
        Pair("wuxia","武侠"),
        Pair("zhichang","职场"),
        Pair("zhentan","侦探"),
        Pair("meishi","美食"),
        Pair("gedou","格斗"),
        Pair("lizhi","励志"),
        Pair("yinyuewudao","音乐舞蹈"),
        Pair("rexue","热血"),
        Pair("zhanzheng","战争"),
        Pair("gaoxiao","搞笑"),
        Pair("shenghuo","生活"),
        Pair("baihe","百合"),
        Pair("mengji","萌系"),
        Pair("jiecao","节操"),
        Pair("xingzhuanhuan","性转换"),
        Pair("yanyi","颜艺"),
        Pair("gufeng","古风"),
        Pair("xianxia","仙侠"),
        Pair("zhaiji","宅系"),
        Pair("juqing","剧情"),
        Pair("shenmo","神魔"),
        Pair("xuanhuan","玄幻"),
        Pair("chuanyue","穿越"),
        Pair("qita","其他"),
        Pair("huanxiang","幻想"),
        Pair("motong","墨瞳"),
        Pair("maimeng","麦萌"),
        Pair("manman","漫漫"),
        Pair("manhuadao","漫画岛"),
        Pair("tuili","推理"),
        Pair("dongfang","东方"),
        Pair("kuaikan","快看"),
        Pair("jizhan","机战"),
        Pair("gaoqingdanxing","高清单行"),
        Pair("xinzuo","新作"),
        Pair("tougao","投稿"),
        Pair("richang","日常"),
        Pair("shougong","手工"),
        Pair("yundong","运动"),
        Pair("weimei","唯美"),
        Pair("dushi","都市"),
        Pair("jingxian","惊险"),
        Pair("jiangshi","僵尸"),
        Pair("lianai","恋爱"),
        Pair("nuexin","虐心"),
        Pair("chunai","纯爱"),
        Pair("fuchou","复仇"),
        Pair("dongzuo","动作"),
        Pair("qita2","其它"),
        Pair("egao","恶搞"),
        Pair("mingxing","明星"),
        Pair("zhenhan","震撼"),
        Pair("anhei","暗黑"),
        Pair("naodong","脑洞"),
        Pair("xuexing","血腥"),
        Pair("youyaoqi","有妖气"),
        Pair("jijia","机甲"),
        Pair("qingchun","青春"),
        Pair("lingyi","灵异"),
        Pair("tongren","同人"),
        Pair("langman","浪漫"),
        Pair("quanmou","权谋"),
        Pair("shehui","社会"),
        Pair("gongdou","宫斗"),
        Pair("baoxiao","爆笑"),
        Pair("tiyu","体育"),
        Pair("lanmu","栏目"),
        Pair("caihong","彩虹"),
        Pair("zhentantuili","侦探推理"),
        Pair("shaonuaiqing","少女爱情"),
        Pair("gaoxiaoxiju","搞笑喜剧"),
        Pair("kongbulingyi","恐怖灵异"),
        Pair("kehuanmohuan","科幻魔幻"),
        Pair("jingjitiyu","竞技体育"),
        Pair("wuxiagedou","武侠格斗"),
        Pair("jianniang","舰娘"),
        Pair("danmeiBL","耽美BL"),
        Pair("xiee","邪恶"),
        Pair("zongheqita","综合其它"),
        Pair("qingnian","青年"),
        Pair("zhainan","宅男"),
        Pair("zazhi","杂志"),
        Pair("yinyue","音乐"),
        Pair("quancai","全彩"),
        Pair("heidao","黑道"),
        Pair("lianaidanmei","恋爱耽美"),
        Pair("rexuemaoxian","热血冒险"),
        Pair("funv","腐女"),
        Pair("gushi","故事"),
        Pair("shaonv","少女"),
        Pair("zongcai","总裁"),
        Pair("baoxiaoxiju","爆笑喜剧"),
        Pair("qitamanhua","其他漫画"),
        Pair("lianaishenghuo","恋爱生活"),
        Pair("kongbuxuanyi","恐怖悬疑"),
        Pair("danmeirensheng","耽美人生"),
        Pair("chongwu","宠物"),
        Pair("zhandou","战斗"),
        Pair("zhaohuanshou","召唤兽"),
        Pair("yineng","异能"),
        Pair("zhuangbi","装逼"),
        Pair("yishijie","异世界"),
        Pair("zhengju","正剧"),
        Pair("wenxin","温馨"),
        Pair("jingqi","惊奇"),
        Pair("jiakong","架空"),
        Pair("qingsong","轻松"),
        Pair("weilai","未来"),
        Pair("keji","科技"),
        Pair("shaonao","烧脑"),
        Pair("gaoxiaoegao","搞笑恶搞"),
        Pair("mhuaquan","mhuaquan"),
        Pair("shaonian","少年"),
        Pair("sigeduoge","四格多格"),
        Pair("bazong","霸总"),
        Pair("xiuzhen","修真"),
        Pair("gushimanhua","故事漫画"),
        Pair("huiben","绘本"),
        Pair("youxi","游戏"),
        Pair("zhenren","真人"),
        Pair("jingsong","惊悚"),
        Pair("manhua","漫画"),
        Pair("weizhongquan","微众圈"),
        Pair("yujie","御姐"),
        Pair("xiaoshuogaibian","小说改编"),
        Pair("luoli","萝莉"),
        Pair("1024manhua","1024manhua"),
        Pair("jiating","家庭"),
        Pair("shenhua","神话"),
        Pair("shishi","史诗"),
        Pair("moshi","末世"),
        Pair("yulequan","娱乐圈"),
        Pair("gandong","感动"),
        Pair("lunli","伦理"),
        Pair("zazhiquanben","杂志全本"),
        Pair("zhiyu2","致郁"),
        Pair("shangzhan","商战"),
        Pair("zhupu","主仆"),
        Pair("manhuaquan","漫画圈"),
        Pair("lianaijuqingmanhua","恋爱、剧情漫画"),
        Pair("hunai","婚爱"),
        Pair("haomen","豪门"),
        Pair("neihan","内涵"),
        Pair("xingzhuan","性转"),
        Pair("xiangcun","乡村"),
        Pair("gongting","宫廷"),
        Pair("duanzi","段子"),
        Pair("chunaimanhua","纯爱漫画"),
        Pair("nixi","逆袭"),
        Pair("hunyin","婚姻"),
        Pair("baihenvxing","百合女性"),
        Pair("shenghuomanhua","生活漫画"),
        Pair("ertong","儿童"),
        Pair("wudao","舞蹈"),
        Pair("tianchong","甜宠"),
        Pair("wengai","文改"),
        Pair("dujia","独家"),
        Pair("biaoqian","标签"),
        Pair("zhaifumanhua","宅腐漫画"),
        Pair("qinggan","情感"),
        Pair("mingkatong","茗卡通"),
        Pair("jiujie","纠结"),
        Pair("lianaimaoxiangaoxiao","恋爱冒险搞笑"),
        Pair("xiuzhenlianaijiakong","修真恋爱架空"),
        Pair("lianaigaoxiaohougong","恋爱搞笑后宫"),
        Pair("xuanyikongbu","悬疑恐怖"),
        Pair("lianaixiaoyuanshenghuo","恋爱校园生活"),
        Pair("xiuzhenlianaigufeng","修真恋爱古风"),
        Pair("shenghuoxuanyilingyi","生活悬疑灵异"),
        Pair("qingnianmanhua","青年漫画"),
        Pair("lishimanhua","历史漫画"),
        Pair("meishaonv","美少女"),
        Pair("shuangliu","爽流"),
        Pair("qiangwei","蔷薇"),
        Pair("gaozhishang","高智商"),
        Pair("xuanyituili","悬疑推理"),
        Pair("jizhi","机智"),
        Pair("donghua","动画"),
        Pair("rexuedongzuo","热血动作"),
        Pair("xiuji","秀吉"),
        Pair("AA","AA"),
        Pair("gaibian","改编"),
        Pair("juwei","橘味")
    ))

    private class letterfilter: UriSelectFilterPath("按字母","filterletter", arrayOf(
        Pair("","全部"),
        Pair("a","A"),
        Pair("b","B"),
        Pair("c","C"),
        Pair("d","D"),
        Pair("e","E"),
        Pair("f","F"),
        Pair("g","G"),
        Pair("h","H"),
        Pair("i","I"),
        Pair("j","J"),
        Pair("k","K"),
        Pair("l","L"),
        Pair("m","M"),
        Pair("n","N"),
        Pair("o","O"),
        Pair("p","P"),
        Pair("q","Q"),
        Pair("r","R"),
        Pair("s","S"),
        Pair("t","T"),
        Pair("u","U"),
        Pair("v","V"),
        Pair("w","W"),
        Pair("x","X"),
        Pair("y","Y"),
        Pair("z","Z"),
        Pair("1","其他")
    ))

    private class statusfilter: UriSelectFilterPath("按进度","filterstatus",arrayOf(
        Pair("","全部"),
        Pair("wanjie","已完结"),
        Pair("lianzai","连载中")
    ))

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilterPath(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                           val firstIsUnspecified: Boolean = true,
                                           defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendPath(vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}

