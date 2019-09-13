package eu.kanade.tachiyomi.extension.zh.dmzj

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dmzj source
 */

class Dmzj : HttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "动漫之家"
    override val baseUrl = "http://v3api.dmzj.com"

    private fun cleanUrl(url: String) = if (url.startsWith("//"))
        "http:$url"
    else url

    private fun myGet(url: String) = GET(url)
            .newBuilder()
            .header("User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/56.0.2924.87 " +
                            "Safari/537.36 " +
                            "Tachiyomi/1.0")
            .build()!!

    // for simple searches (query only, no filters)
    private fun simpleSearchJsonParse(json: String): MangasPage {
        val arr = JSONArray(json)
        val ret = ArrayList<SManga>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cid = obj.getString("id")
            ret.add(SManga.create().apply {
                title = obj.getString("comic_name")
                thumbnail_url = cleanUrl(obj.getString("comic_cover"))
                author = obj.optString("comic_author")
                url = "/comic/comic_$cid.json"
            })
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
            ret.add(SManga.create().apply {
                title = obj.getString("title")
                thumbnail_url = obj.getString("cover")
                author = obj.optString("authors")
                status = when(obj.getString("status")) {
                    "已完结" -> SManga.COMPLETED
                    "连载中" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
                url = "/comic/comic_$cid.json"
            })
        }
        return MangasPage(ret, arr.length() != 0)
    }

    override fun popularMangaRequest(page: Int) = myGet("http://v2.api.dmzj.com/classify/0/0/${page-1}.json")

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = myGet("http://v2.api.dmzj.com/classify/0/1/${page-1}.json")

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query != "") {
            val uri = Uri.parse("http://s.acg.dmzj.com/comicsum/search.php").buildUpon()
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

            val order = filters.filter { it is SortFilter }.map { (it as UriPartFilter).toUriPart() }.joinToString("")

            return myGet("http://v2.api.dmzj.com/classify/$params/$order/${page-1}.json")
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
        status = when(obj.getJSONArray("status").getJSONObject(0).getInt("tag_id")) {
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
                ret.add(SChapter.create().apply {
                    name = "$prefix: ${chapter.getString("chapter_title")}"
                    date_upload = chapter.getString("updatetime").toLong()*1000 //milliseconds
                    url = "/chapter/$cid/${chapter.getString("chapter_id")}.json"
                })
            }
        }
        return ret
    }

    override fun pageListParse(response: Response): List<Page> {
        val obj = JSONObject(response.body()!!.string())
        val arr = obj.getJSONArray("page_url")
        val ret = ArrayList<Page>(arr.length())
        for (i in 0 until arr.length()) {
            ret.add(Page(i, "", arr.getString(i)))
        }
        return ret
    }

    //Unused, we can get image urls directly from the chapter page
    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList(
            SortFilter(),
            GenreGroup(),
            StatusFilter(),
            TypeFilter(),
            ReaderFilter()
    )

    private class GenreGroup : UriPartFilter("分类", arrayOf(
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
    ))

    private class StatusFilter : UriPartFilter("连载状态", arrayOf(
            Pair("全部", ""),
            Pair("连载", "2309"),
            Pair("完结", "2310")
    ))

    private class TypeFilter : UriPartFilter("地区", arrayOf(
            Pair("全部", ""),
            Pair("日本", "2304"),
            Pair("韩国", "2305"),
            Pair("欧美", "2306"),
            Pair("港台", "2307"),
            Pair("内地", "2308"),
            Pair("其他", "8453")
    ))

    private class SortFilter : UriPartFilter("排序", arrayOf(
            Pair("人气", "0"),
            Pair("更新", "1")
    ))

    private class ReaderFilter : UriPartFilter("读者", arrayOf(
            Pair("全部", ""),
            Pair("少年", "3262"),
            Pair("少女", "3263"),
            Pair("青年", "3264")
    ))

    //Headers
    override fun headersBuilder()
            = super.headersBuilder().add("Referer", "http://www.dmzj.com/")!!

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>,
                                     defaultValue: Int = 0) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }
}
