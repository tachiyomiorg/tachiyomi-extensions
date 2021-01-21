package eu.kanade.tachiyomi.extension.zh.qiximh

import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class Qiximh : HttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "七夕漫画"
    override val baseUrl = "http://qiximh1.com"
    // This is hard limit by API
    val maxPage = 5

    // Used in Rank API
    private enum class RANKTYPE(val rankVal: Int) {
        DAILY_HOT(1),
        WEEKLY_HOT(2),
        MONTHLY_HOT(3),
        OVERALL_HOT(4),
        LATEST(5),
        NEW(6),
    }

    // Used in Sort API (although it looks like genre)
    private enum class SORTTYPE(val sortVal: Int) {
        ADVENTURE(1),
        ACTION(2),
        MAGIC_SCIFI(3),
        THRILLER(4),
        ROMANCE(5),
        SLICE_OF_LIFE(6),
        // These are not accurate, hence not included
        // HIGH_QUALITY(11),
        // ON_GOING(12),
        // COMPLETED(13)
    }

    private open class PairIntFilter(displayName: String, val vals: Array<Pair<String, Int?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun getVal() = vals[state].second
    }

    // Override
    private fun FormBody.value(name: String): String {
        return (0 until size())
            .first { name(it) == name }
            .let { value(it) }
    }

    private fun commonRankDataRequest(page: Int, rankTypeVal: Int): Request {
        return POST(
            "$baseUrl/rankdata.php",
            headers,
            FormBody.Builder()
                .add("page_num", page.toString())
                .add("type", rankTypeVal.toString())
                .build()
        )
    }

    private fun commonSortDataRequest(page: Int, sortTypeVal: Int): Request {
        return POST(
            "$baseUrl/sortdata.php",
            headers,
            FormBody.Builder()
                .add("page_num", page.toString())
                .add("type", sortTypeVal.toString())
                .build()
        )
    }

    private fun commonDataProcess(origRequest: Request, responseBody: String): MangasPage {
        val jsonData = JSONArray(responseBody)

        val mangaArr = mutableListOf<SManga>()

        for (i in 0 until jsonData.length()) {
            val targetObj = jsonData.getJSONObject(i)
            mangaArr.add(
                SManga.create().apply {
                    title = targetObj.get("name") as String
                    status = SManga.UNKNOWN
                    thumbnail_url = targetObj.get("imgurl") as String
                    url = "$baseUrl/${targetObj.get("id")}/"
                }
            )
        }

        val requestBody = origRequest.body() as FormBody
        val currentPage: Int = requestBody.value("page_num").toInt()
        val hasNextPage = currentPage < maxPage

        return MangasPage(mangaArr, hasNextPage)
    }

    private fun commonRankDataParse(response: Response): MangasPage {
        return commonDataProcess(response.request(), response.body()!!.string())
    }

    // Popular Manga
    override fun popularMangaRequest(page: Int) = commonRankDataRequest(page, RANKTYPE.DAILY_HOT.rankVal)
    override fun popularMangaParse(response: Response) = commonRankDataParse(response)

    // Latest Updates
    override fun latestUpdatesRequest(page: Int) = commonRankDataRequest(page, RANKTYPE.LATEST.rankVal)
    override fun latestUpdatesParse(response: Response) = commonRankDataParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return POST(
                "$baseUrl/search.php",
                headers,
                FormBody.Builder()
                    .add("keyword", query)
                    .build()
            )
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is RankFilter -> {
                        val filterVal = filter.getVal()
                        if (filterVal != null) {
                            return commonRankDataRequest(page, filterVal)
                        }
                    }
                    is SortFilter -> {
                        val filterVal = filter.getVal()
                        if (filterVal != null) {
                            return commonSortDataRequest(page, filterVal)
                        }
                    }
                }
            }

            // Default if no filter set
            return commonRankDataRequest(page, RANKTYPE.DAILY_HOT.rankVal)
        }
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val responseBody = response.body()
        val mangaArr = mutableListOf<SManga>()

        if (responseBody != null) {
            val responseString = responseBody.string()
            if (!responseString.isNullOrEmpty()) {
                if (responseString.startsWith("[")) {
                    // This is to process filter
                    return commonDataProcess(response.request(), responseString)
                } else {
                    val jsonData = JSONObject(responseString)
                    if (jsonData.get("msg") == "success") {
                        val jsonArr = jsonData.getJSONArray("search_data")

                        for (i in 0 until jsonArr.length()) {
                            val targetObj = jsonArr.getJSONObject(i)
                            mangaArr.add(
                                SManga.create().apply {
                                    title = targetObj.get("name") as String
                                    thumbnail_url = targetObj.get("imgs") as String
                                    url = "$baseUrl/${targetObj.get("id")}/"
                                }
                            )
                        }
                    }
                }
            }
        }

        // Search does not have pagination
        return MangasPage(mangaArr, false)
    }

    // Filter
    private class RankFilter : PairIntFilter(
        "排行榜",
        arrayOf(
            Pair("全部", null),
            Pair("日热门榜", RANKTYPE.DAILY_HOT.rankVal),
            Pair("周热门榜", RANKTYPE.WEEKLY_HOT.rankVal),
            Pair("月热门榜", RANKTYPE.MONTHLY_HOT.rankVal),
            Pair("总热门榜", RANKTYPE.OVERALL_HOT.rankVal),
            Pair("最近更新", RANKTYPE.LATEST.rankVal),
            Pair("新漫入库", RANKTYPE.NEW.rankVal),
        )
    )

    private class SortFilter : PairIntFilter(
        "分类",
        arrayOf(
            Pair("全部", null),
            Pair("冒险热血", SORTTYPE.ADVENTURE.sortVal),
            Pair("武侠格斗", SORTTYPE.ACTION.sortVal),
            Pair("玄幻科幻", SORTTYPE.MAGIC_SCIFI.sortVal),
            Pair("侦探推理", SORTTYPE.THRILLER.sortVal),
            Pair("耽美爱情", SORTTYPE.ROMANCE.sortVal),
            Pair("生活漫画", SORTTYPE.SLICE_OF_LIFE.sortVal),
        )
    )

    override fun getFilterList() = FilterList(
        Filter.Header("注意： 文本搜索，排行榜和分类筛选，不可同时使用"),
        Filter.Separator(),
        RankFilter(),
        SortFilter()
    )

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers)
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("h1.name").text()

            author = document.select(".author_name").text()

            description = arrayOf(
                document.select("span.looking_chapter").text(),
                document.select(".bold_fortime").text(),
                document.select(".details").first().ownText(),
            ).filter(String::isNotBlank).joinToString("\n")

            genre = arrayOf(
                document.select(".comic_hot span:last-child").text(),
                *(document.select(".tags.tags_last").text().split("|").toTypedArray())
            ).filter(String::isNotBlank).joinToString()
        }
    }

    // Chapter
    override fun chapterListRequest(manga: SManga) = GET(manga.url, headers)
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // API does not allow retrieve full chapter list, hence the need to parse the chapters from both HTML and API
        val htmlChapters = document.select(".catalog_list.row_catalog_list a").map {
            SChapter.create().apply {
                name = it.text()
                url = "$baseUrl${it.attr("href")}"
            }
        }

        val mangaUrl = response.request().url().toString()

        val request = POST(
            "$baseUrl/bookchapter/",
            headers,
            FormBody.Builder()
                .add("id", mangaUrl.split("/").toTypedArray().filter(String::isNotBlank).last())
                .add("id2", "1")
                .build()
        )

        val inlineResponse = client.newCall(request).execute()
        val jsonData = JSONArray(inlineResponse.body()!!.string())

        val chapterArr = mutableListOf<SChapter>()
        chapterArr.addAll(htmlChapters)

        for (i in 0 until jsonData.length()) {
            val targetObj = jsonData.getJSONObject(i)
            chapterArr.add(
                SChapter.create().apply {
                    name = targetObj.get("chaptername") as String
                    url = "$mangaUrl${targetObj.get("chapterid")}.html"
                }
            )
        }

        return chapterArr
    }

    // Page
    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // Special thanks to author who created Mangahere.kt
        val duktape = Duktape.create()

        val script = document.select("script:containsData(function(p,a,c,k,e,d))").html().removePrefix("eval")
        val deobfuscatedScript = duktape.evaluate(script).toString()
        val urls = deobfuscatedScript.substringAfter("newImgs=[\"").substringBefore("\"]").split("\",\"")
        duktape.close()

        return urls.mapIndexed { index, s -> Page(index, "", s) }
    }

    // Unused
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused")
}
