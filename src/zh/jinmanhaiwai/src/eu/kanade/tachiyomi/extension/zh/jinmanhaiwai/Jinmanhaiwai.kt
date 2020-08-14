package eu.kanade.tachiyomi.extension.zh.jinmanhaiwai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class Jinmanhaiwai : ParsedHttpSource() {
    override val baseUrl: String = "https://18comic.vip"
    override val lang: String = "zh"
    override val name: String = "禁漫(海外IP访问)"
    override val supportsLatest: Boolean = true

    private var chapterArea = "a[class=col btn btn-primary dropdown-toggle reading]"

    // 点击量排序(人气)
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/albums?o=mv&page=$page", headers)
    }

    override fun popularMangaNextPageSelector(): String? = "a.prevnext"
    override fun popularMangaSelector(): String = "div.col-xs-6.col-sm-6.col-md-4.col-lg-3.list-col div.well.well-sm"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("span.video-title").text()
        setUrlWithoutDomain(element.select("a").first().attr("href"))
        thumbnail_url = element.select("img").attr("data-original").split("\\?")[0]
        author = element.select("div.title-truncate").select("a").first().text()
    }

    // 最新排序
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/albums?o=mr&page=$page", headers)
    }

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // 查询信息
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query != "") {
            val url = HttpUrl.parse("$baseUrl/search/photos?search_query=$query&page=$page")?.newBuilder()
            GET(url.toString(), headers)
        } else {
            val params = filters.map {
                if (it is UriPartFilter) {
                    it.toUriPart()
                } else ""
            }.filter { it != "" }.joinToString("?")
            val url = HttpUrl.parse("$baseUrl/albums$params&page=$page")?.newBuilder()
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // 漫画详情
    // url网址 , title标题 , artist艺术家 , author作者 , description描述 , genre类型 , thumbnail_url缩图网址 , initialized是否初始化
    // status状态 0未知,1连载,2完结,3领取牌照

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        determineChapterInfo(document)
        title = document.select("div.panel-heading").select("div.pull-left").first().text()
        thumbnail_url = document.select("img.lazy_img.img-responsive").attr("src").split("\\?")[0]
        author = selectAuthor(document)
        artist = "插件开发者:ZongerZY"
        genre = selectDetailsStatusAndGenre(document, 0)
        status = selectDetailsStatusAndGenre(document, 1).trim()!!.toInt()
        description = document.select("div.p-t-5.p-b-5").get(7).text() + " >>>插件开发者[twitter:ZongerZY][GitHub:ZongerZY][Discord:ZongerZY#3770],开发者将负责开发中文扩展插件,希望大家关注并支持开发者<<<"
    }

    // 查询作者信息
    private fun selectAuthor(document: Document): String {
        var element = document.select("div.tag-block").get(9)
        if (element.select("a").size == 0) {
            return "(插件):ZongerZY"
        } else {
            return element.select("a").first().text()
        }
    }
    // 查询漫画状态和类别信息
    private fun selectDetailsStatusAndGenre(document: Document, index: Int): String {
        determineChapterInfo(document)
        var status: String = "0"
        var genre: String = ""
        if (document.select("span[itemprop=genre] a").size == 0) {
            if (index == 1) {
                return status
            } else {
                return genre
            }
        }
        var elements: Elements = document.select("span[itemprop=genre]").first().select("a")
        for (value in elements) {
            var vote: String = value.select("a").text()
            if (vote.equals("連載中")) {
                status = "1"
            } else if (vote.equals("完結")) {
                status = "2"
            } else {
                genre = genre + "$vote "
            }
        }
        if (index == 1) {
            return status
        } else {
            return genre
        }
    }

    // 漫画章节信息
    override fun chapterListSelector(): String = chapterArea
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {

        if (chapterArea == "a[class=col btn btn-primary dropdown-toggle reading]") {
            name = element.select("a[class=col btn btn-primary dropdown-toggle reading]").text()
            url = element.select("a[class=col btn btn-primary dropdown-toggle reading]").attr("href")
        } else {
            url = element.select("a").attr("href")
            name = element.select("a li").first().ownText()
        }
    }

    private fun determineChapterInfo(document: Document) {
        if (document.select("div[id=episode-block] a li").size == 0) {
            chapterArea = "a[class=col btn btn-primary dropdown-toggle reading]"
        } else {
            chapterArea = "div[id=episode-block] a[href^=/photo/]"
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        var elements = document.select("div[style=text-align:center;][id*=0]")
        for (element in elements) {
            if (element.select("div[style=text-align:center;][id*=0] img").attr("src").indexOf("blank.jpg") >= 0) {
                add(Page(size, "", element.select("div[style=text-align:center;][id*=0] img").attr("data-original").split("\\?")[0]))
            } else {
                add(Page(size, "", element.select("div[style=text-align:center;][id*=0] img").attr("src").split("\\?")[0]))
            }
        }
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")

    // Filters

    override fun getFilterList() = FilterList(
        CategoryGroup(),
        ProgressGroup()
    )

    private class CategoryGroup : UriPartFilter("按类型", arrayOf(
        Pair("全部", ""),
        Pair("其他", "/another"),
        Pair("同人", "/doujin"),
        Pair("韩漫", "/hanman"),
        Pair("美漫", "/meiman"),
        Pair("短篇", "/short"),
        Pair("单本", "/single")
    ))

    private class ProgressGroup : UriPartFilter("按进度", arrayOf(
        Pair("最新", "o=mr"),
        Pair("最多点阅", "o=mv"),
        Pair("最多图片", "o=mp"),
        Pair("最多爱心", "o=tf")
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
