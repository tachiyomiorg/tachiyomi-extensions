package eu.kanade.tachiyomi.extension.zh.imitui

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Imitui : ParsedHttpSource() {
    override val name: String = "爱米推漫画"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://m.imitui.com"

    // Popular

    // Override to set hasNextPage according to page number
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val totalPage = document.select("input#total-page").attr("value").toInt()
        val index = document.location().lastIndexOf("page=") + 5
        val currentPage = document.location().substring(index).toInt()
        val hasNextPage = currentPage < totalPage

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/click/?page=$page", headers)
    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException("Not used.")
    override fun popularMangaSelector(): String = "ul#comic-items > li.list-comic"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.txtA").text()
        setUrlWithoutDomain(element.select("a.txtA").attr("href"))
        thumbnail_url = element.select("a.ImgA > img").attr("src")
    }

    // Latest

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/list/update/?page=$page", headers)
    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException("Not used.")
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search

    // Override to set hasNextPage according to page number
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val totalPage = document.select("input#total-page").attr("value").toInt()
        val index = document.location().lastIndexOf("page=") + 5
        val currentPage = document.location().substring(index).toInt()
        val hasNextPage = currentPage < totalPage

        return MangasPage(mangas, hasNextPage)
    }
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
                if (it is UriSelectFilterPath)
                    it.addToUri(pathBuilder)
            }
            val filterPath = pathBuilder.toString().replace("/", "-").removePrefix("-")
            uri.appendEncodedPath(filterPath)
                .appendEncodedPath("")
                .appendQueryParameter("page", page.toString())
        }
        return GET(uri.toString(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException("Not used.")
    override fun searchMangaSelector(): String = "div.itemBox, li.list-comic"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.title, a.txtA").text()
        setUrlWithoutDomain(element.select("a.title, a.txtA").attr("href"))
        thumbnail_url = element.select("div.itemImg > a > img, a.ImgA > img").attr("src")
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("div#comicName").text()
        thumbnail_url = document.select("div#Cover > img").attr("abs:src")
        author = document.select("div.sub_r > p:nth-child(1)").text()
        artist = author
        // Sometimes there is one a tag with href="/list/" and empty text, exclude it
        val genreElement = document.select("div.sub_r > p.txtItme > a").not("[href=/list/]")
        genre = genreElement.text().replace(" ", ", ")
        description = if (document.select("p#full-des").isEmpty())
            document.select("p#simple-des").text()
        else
            document.select("p#full-des").text()
        status = when {
            !document.select("p.txtItme > a[href=/list/lianzai/]").isEmpty() -> SManga.ONGOING
            !document.select("p.txtItme > a[href=/list/wanjie/]").isEmpty() -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    // The last element is not a real chapter, so exclude it
    override fun chapterListSelector(): String = "ul#chapter-list-1 > li:nth-last-child(n+2)"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("span").text()
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val totalPage = document.select("div.image-content > p").text().substring(2)
        val location = document.location()
        add(Page(size, location))

        val insertIndex = location.lastIndexOf(".html")
        for (i in 2..totalPage.toInt()) {
            add(Page(size, location.replaceRange(insertIndex, insertIndex, "-$i")))
        }
    }

    override fun imageUrlParse(document: Document): String =
        document.select("div.image-content > img#image").attr("src")

    // Some images need referer to load
    override fun imageRequest(page: Page): Request {
        val newHeader = headers.newBuilder().add("Referer: https://m.imitui.com/").build()
        return GET(page.imageUrl!!, newHeader)
    }

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("如果使用文本搜索"),
        Filter.Header("过滤器将被忽略"),
        GenreFilter(),
        ReaderFilter(),
        StatusFilter(),
        RegionFilter()
    )

    private class GenreFilter : UriSelectFilterPath(
        "题材",
        arrayOf(
            Pair("", "全部"),
            Pair("rexue", "热血"),
            Pair("xuanhuan", "玄幻"),
            Pair("xiuzhen", "修真"),
            Pair("gufeng", "古风"),
            Pair("lianai", "恋爱"),
            Pair("chuanyue", "穿越"),
            Pair("dushi", "都市"),
            Pair("bazong", "霸总"),
            Pair("xuanyi", "悬疑"),
            Pair("gaoxiao", "搞笑"),
            Pair("qihuan", "奇幻"),
            Pair("zongcai", "总裁"),
            Pair("richang", "日常"),
            Pair("maoxian", "冒险"),
            Pair("kehuan", "科幻"),
            Pair("chunai", "纯爱"),
            Pair("mohuan", "魔幻"),
            Pair("zhanzheng", "战争"),
            Pair("qiangwei", "蔷薇"),
            Pair("wuxia", "武侠"),
            Pair("shenghuo", "生活"),
            Pair("dongzuo", "动作"),
            Pair("hougong", "后宫"),
            Pair("youxi", "游戏"),
            Pair("kongbu", "恐怖"),
            Pair("mangai", "漫改"),
            Pair("zhenren", "真人"),
            Pair("xiaoyuan", "校园"),
            Pair("juqing", "剧情"),
            Pair("lingyi", "灵异"),
            Pair("shaonian", "少年"),
            Pair("tuili", "推理"),
            Pair("huaijiu", "怀旧"),
            Pair("qinggan", "情感"),
            Pair("ouxiang", "偶像"),
            Pair("shaonv", "少女"),
            Pair("dujia", "独家"),
            Pair("nuexin", "虐心"),
            Pair("baoxiao", "爆笑"),
            Pair("lizhi", "励志"),
            Pair("meishi", "美食"),
            Pair("fuchou", "复仇"),
            Pair("caihong", "彩虹"),
            Pair("weimei", "唯美"),
            Pair("zhiyu", "治愈"),
            Pair("mingxing", "明星"),
            Pair("naodong", "脑洞"),
            Pair("mofa", "魔法"),
            Pair("xiuxian", "修仙"),
            Pair("zhongsheng", "重生"),
            Pair("xianxia", "仙侠"),
            Pair("moshi", "末世"),
            Pair("yineng", "异能"),
            Pair("nvzun", "女尊"),
            Pair("qita", "其它"),
            Pair("yanqing", "言情"),
            Pair("danmei", "耽美"),
            Pair("yundong", "运动"),
            Pair("gongdou", "宫斗"),
            Pair("guzhuang", "古装"),
            Pair("meishaonv", "美少女"),
            Pair("shenmo", "神魔"),
            Pair("lishi", "历史"),
            Pair("jingxian", "惊险"),
            Pair("jingji", "竞技"),
            Pair("mengxi", "萌系"),
            Pair("tiyu", "体育"),
            Pair("gedou", "格斗"),
            Pair("jijia", "机甲"),
            Pair("nuelian", "虐恋"),
            Pair("shuang", "爽"),
            Pair("fuli", "福利"),
            Pair("qita2", "其他"),
            Pair("xiaojiangshi", "小僵尸"),
            Pair("jiangshi", "僵尸"),
            Pair("langman", "浪漫"),
            Pair("jinshouzhi", "金手指"),
            Pair("yujie", "御姐"),
            Pair("zhandou", "战斗"),
            Pair("egao", "恶搞"),
            Pair("shehui", "社会"),
            Pair("quanmou", "权谋"),
            Pair("qingchun", "青春"),
            Pair("luoli", "萝莉"),
            Pair("tongren", "同人"),
            Pair("zhenhan", "震撼"),
            Pair("riman", "日漫"),
            Pair("junfa", "军阀"),
            Pair("minguo", "民国"),
            Pair("tegong", "特工"),
            Pair("meinv", "美女"),
            Pair("jiandie", "间谍"),
            Pair("anhei", "暗黑"),
            Pair("jiecao", "节操"),
            Pair("jingdian", "经典"),
            Pair("youmo", "幽默"),
            Pair("tianchong", "甜宠"),
            Pair("shenhua", "神话"),
            Pair("riben", "日本"),
            Pair("yijiyuan", "翼纪元"),
            Pair("tiaoman", "条漫"),
            Pair("LOL", "LOL"),
            Pair("zhongtian", "种田"),
            Pair("duanpian", "短篇"),
            Pair("jingsong", "惊悚"),
            Pair("sige", "四格"),
            Pair("guoman", "国漫"),
            Pair("youqudao", "有趣岛"),
            Pair("mengchong", "萌宠"),
            Pair("renxing", "人性"),
            Pair("chonghun", "宠婚"),
            Pair("xinqi", "新妻"),
            Pair("xixiegui", "吸血鬼"),
            Pair("shenjiemanhua", "神界漫画"),
            Pair("xueyuehua", "雪月花"),
            Pair("mengqishi", "梦骑士"),
            Pair("shouer", "兽耳"),
            Pair("shaoer", "少儿"),
            Pair("baihe", "百合"),
            Pair("chiji", "吃鸡"),
            Pair("qiangzhan", "枪战"),
            Pair("tezhongbing", "特种兵"),
            Pair("xiongdi", "兄弟"),
            Pair("yishi", "异世"),
            Pair("xiongmei", "兄妹"),
            Pair("sanciyuan", "三次元"),
            Pair("meixing", "美型"),
            Pair("haomen", "豪门"),
            Pair("hunchong", "婚宠"),
            Pair("kaigua", "开挂"),
            Pair("xuexing", "血腥"),
            Pair("qingsong", "轻松"),
            Pair("yangcheng", "养成"),
            Pair("tishen", "替身"),
            Pair("nanshen", "男神"),
            Pair("qingqingshu", "青青树"),
            Pair("yishijie", "异世界"),
            Pair("nanchuannv", "男穿女"),
            Pair("hunchuan", "魂穿"),
            Pair("yinan", "阴暗"),
            Pair("dujitang", "毒鸡汤"),
            Pair("pianyu", "片玉"),
            Pair("manhuahui", "漫画会"),
            Pair("longren", "龙刃"),
            Pair("xihuan", "喜欢"),
            Pair("zhaohuan", "召唤"),
            Pair("yijie", "异界"),
            Pair("henxiyou", "狠西游"),
            Pair("xiyouji", "西游记"),
            Pair("jianghu", "江湖"),
            Pair("duantoudao", "断头岛"),
            Pair("hanman", "韩漫"),
            Pair("bingjiao", "病娇"),
            Pair("changpian", "长篇"),
            Pair("chenlan", "陈岚"),
            Pair("aiqing", "爱情"),
            Pair("nvqiang", "女强"),
            Pair("ranxiang", "燃向"),
            Pair("tianshangkong", "天上空"),
            Pair("duzhiniao", "渡之鸟"),
            Pair("xuezu", "血族"),
            Pair("mowang", "魔王"),
            Pair("keai", "可爱"),
            Pair("gongting", "宫廷"),
            Pair("hunlian", "婚恋"),
            Pair("meng", "萌"),
            Pair("ashuai", "阿衰"),
            Pair("sanjiaolian", "三角恋"),
            Pair("qianshi", "前世"),
            Pair("lunhui", "轮回"),
            Pair("jingqi", "惊奇"),
            Pair("zhentan", "侦探"),
            Pair("huanlexiang", "欢乐向"),
            Pair("zhichang", "职场"),
            Pair("gandong", "感动"),
            Pair("jiakong", "架空"),
            Pair("qingxiaoshuo", "轻小说"),
            Pair("yanyi", "颜艺"),
            Pair("xingzhuanhuan", "性转换"),
            Pair("dongfang", "东方"),
            Pair("danmeiBL", "耽美BL"),
            Pair("qingsonggaoxiao", "轻松搞笑"),
            Pair("tongrenmanhua", "同人漫画"),
            Pair("xiaoyuangaoxiaoshenghuo", "校园搞笑生活"),
            Pair("shaonvaiqing", "少女爱情"),
            Pair("zhengju", "正剧"),
            Pair("shaonao", "烧脑"),
            Pair("zhuangbi", "装逼"),
            Pair("shengui", "神鬼"),
            Pair("weiniang", "伪娘"),
            Pair("gaoqingdanxing", "高清单行"),
            Pair("gushimanhua", "故事漫画"),
            Pair("lianaishenghuoxuanhuan", "恋爱生活玄幻"),
            Pair("xifangmohuan", "西方魔幻"),
            Pair("jianniang", "舰娘"),
            Pair("zhaixi", "宅系"),
            Pair("shangzhan", "商战"),
            Pair("shuangliu", "爽流"),
            Pair("rexuemaoxian", "热血冒险"),
            Pair("keji", "科技"),
            Pair("wenxin", "温馨"),
            Pair("jiating", "家庭"),
            Pair("hunyin", "婚姻"),
            Pair("duanzi", "段子"),
            Pair("neihan", "内涵"),
            Pair("jizhan", "机战"),
            Pair("yulequan", "娱乐圈"),
            Pair("weilai", "未来"),
            Pair("chongwu", "宠物"),
            Pair("bazonglianaixuanhuan", "霸总恋爱玄幻"),
            Pair("gushi", "故事"),
            Pair("yinyuewudao", "音乐舞蹈"),
            Pair("nixi", "逆袭"),
            Pair("zhaohuanshou", "召唤兽"),
            Pair("kehuanmohuan", "科幻魔幻"),
            Pair("jiujie", "纠结"),
            Pair("lunli", "伦理"),
            Pair("lianaishenghuo", "恋爱生活"),
            Pair("xinzuo", "新作"),
            Pair("lishimanhua", "历史漫画"),
            Pair("ertong", "儿童"),
            Pair("zhentantuili", "侦探推理"),
            Pair("xiuzhenlianaijiakong", "修真恋爱架空"),
            Pair("shougong", "手工"),
            Pair("qingnian", "青年"),
            Pair("qitamanhua", "其他漫画"),
            Pair("zhiyu2", "致郁"),
            Pair("shishi", "史诗"),
            Pair("xiuji", "秀吉"),
            Pair("xiangcun", "乡村"),
            Pair("xingzhuan", "性转"),
            Pair("hunai", "婚爱"),
            Pair("siwang", "死亡"),
            Pair("sishen", "死神"),
            Pair("shaonan", "少男"),
            Pair("xuanyijingsong", "悬疑、惊悚"),
            Pair("baoxiaoxiju", "爆笑喜剧"),
            Pair("dongzuogedou", "动作格斗"),
            Pair("gaibian", "改编"),
            Pair("AA", "AA"),
            Pair("lianaidanmei", "恋爱耽美"),
            Pair("heidao", "黑道"),
            Pair("guiguai", "鬼怪"),
            Pair("sangshi", "丧尸"),
            Pair("zhupu", "主仆"),
            Pair("zhiyinmanke", "知音漫客"),
            Pair("maimeng", "麦萌"),
            Pair("nizhuan", "逆转"),
            Pair("danvzhu", "大女主"),
            Pair("aimei", "暧昧"),
            Pair("shenghua", "生化"),
            Pair("qiwen", "奇闻"),
            Pair("zhaidou", "宅斗"),
            Pair("lanmu", "栏目"),
            Pair("guaitan", "怪谈"),
            Pair("chongai", "宠爱"),
            Pair("huanxiang", "幻想"),
            Pair("yizu", "异族"),
            Pair("tanan", "探案"),
            Pair("panni", "叛逆"),
            Pair("juwei", "橘味"),
            Pair("yinv", "乙女"),
            Pair("lieqi", "猎奇"),
            Pair("rigeng", "日更"),
            Pair("manman", "漫漫"),
            Pair("zhidou", "智斗"),
            Pair("zhengnengliang", "正能量"),
            Pair("manhuayifan", "漫画一番"),
            Pair("nvwangdiankeng", "女王点坑"),
            Pair("mankezhan", "漫客栈"),
            Pair("samanhua", "飒漫画"),
            Pair("xiaoshuogaibian", "小说改编"),
            Pair("shenshi", "绅士"),
            Pair("kongbuxuanyi", "恐怖悬疑"),
            Pair("huiben", "绘本"),
            Pair("yinyue", "音乐"),
            Pair("huxian", "狐仙"),
            Pair("sihoushijie", "死后世界"),
            Pair("motong", "墨瞳"),
            Pair("manhua", "漫画"),
            Pair("mori", "末日"),
            Pair("xitong", "系统"),
            Pair("shenxian", "神仙"),
            Pair("youyaoqi", "有妖气"),
            Pair("guaiwu", "怪物"),
            Pair("yaoguai", "妖怪"),
            Pair("shenhao", "神豪"),
            Pair("bazongdushi", "霸总.都市"),
            Pair("gaotian", "高甜"),
            Pair("xianzhiji", "限制级"),
            Pair("dianjing", "电竞"),
            Pair("unknown", "ゆり"),
            Pair("xiongdiqing", "兄弟情"),
            Pair("nuanmeng", "暖萌"),
            Pair("haokuai", "豪快"),
            Pair("wanjie", "完结"),
            Pair("nvsheng", "女生"),
            Pair("lianzai", "连载"),
            Pair("nansheng", "男生"),
            Pair("futa", "扶她"),
            Pair("bianshenlongyurentishiyanshenghuaweiji", "变身;龙鱼;人体实验;生化危机"),
            Pair("shenghuomanhua", "生活漫画"),
            Pair("huanxi", "欢喜"),
            Pair("beiou", "北欧"),
            Pair("fuhei", "腹黑"),
            Pair("xihuan2", "西幻"),
            Pair("qinqing", "亲情"),
            Pair("gudai", "古代"),
            Pair("jifu", "基腐"),
            Pair("langmanaiqing", "浪漫爱情"),
            Pair("BL", "BL"),
            Pair("qihuanmaoxian", "奇幻冒险"),
            Pair("youmogaoxiao", "幽默搞笑"),
            Pair("gufengchuanyue", "古风穿越"),
            Pair("zongheqita", "综合其它"),
            Pair("TS", "TS")
        )
    )

    private class ReaderFilter : UriSelectFilterPath(
        "读者",
        arrayOf(
            Pair("", "全部"),
            Pair("ertong", "儿童漫画"),
            Pair("shaonian", "少年漫画"),
            Pair("shaonv", "少女漫画"),
            Pair("qingnian", "青年漫画")
        )
    )

    private class StatusFilter : UriSelectFilterPath(
        "进度",
        arrayOf(
            Pair("", "全部"),
            Pair("wanjie", "已完结"),
            Pair("lianzai", "连载中")
        )
    )

    private class RegionFilter : UriSelectFilterPath(
        "地区",
        arrayOf(
            Pair("", "全部"),
            Pair("riben", "日本"),
            Pair("dalu", "大陆"),
            Pair("hongkong", "香港"),
            Pair("taiwan", "台湾"),
            Pair("oumei", "欧美"),
            Pair("hanguo", "韩国"),
            Pair("qita", "其他")
        )
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     */
    // vals: <name, display>
    private open class UriSelectFilterPath(
        displayName: String,
        val vals: Array<Pair<String, String>>
    ) : Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun addToUri(uri: Uri.Builder) {
            if (state != 0)
                uri.appendPath(vals[state].first)
        }
    }
}
