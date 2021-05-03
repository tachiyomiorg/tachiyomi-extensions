package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable

class Kuaikanmanhua : HttpSource() {

    override val name = "Kuaikanmanhua"

    override val baseUrl = "https://www.kuaikanmanhua.com"

    override val lang = "zh"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val apiUrl = "https://api.kkmh.com"

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/v1/topic_new/lists/get_by_tag?tag=0&since=${(page - 1) * 10}", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val jsonList = JSONObject(body).getJSONObject("data").getJSONArray("topics")
        return parseMangaJsonArray(jsonList)
    }

    private fun parseMangaJsonArray(jsonList: JSONArray, isSearch: Boolean = false): MangasPage {
        val mangaList = mutableListOf<SManga>()

        for (i in 0 until jsonList.length()) {
            val obj = jsonList.getJSONObject(i)
            mangaList.add(
                SManga.create().apply {
                    title = obj.getString("title")
                    thumbnail_url = obj.getString("vertical_image_url")
                    url = "/web/topic/" + obj.getInt("id")
                }
            )
        }
        // KKMH does not have pages when you search
        return MangasPage(mangaList, mangaList.size > 9 && !isSearch)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/v1/topic_new/lists/get_by_tag?tag=19&since=${(page - 1) * 10}", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(TOPIC_ID_SEARCH_PREFIX)) {
            val new_query = query.removePrefix(TOPIC_ID_SEARCH_PREFIX)
            return client.newCall(GET("$apiUrl/v1/topics/$new_query"))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/web/topic/$new_query"
                    MangasPage(listOf(details), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {

            GET("$apiUrl/v1/search/topic?q=$query&size=18", headers)
        } else {
            lateinit var genre: String
            lateinit var status: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        genre = filter.toUriPart()
                    }
                    is StatusFilter -> {
                        status = filter.toUriPart()
                    }
                }
            }
            GET("$apiUrl/v1/search/by_tag?since=${(page - 1) * 10}&tag=$genre&sort=1&query_category=%7B%22update_status%22:$status%7D")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val jsonObj = JSONObject(body).getJSONObject("data")
        if (jsonObj.has("hit")) {
            return parseMangaJsonArray(jsonObj.getJSONArray("hit"), true)
        }

        return parseMangaJsonArray(jsonObj.getJSONArray("topics"), false)
    }

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        // Convert the stored url to one that works with the api
        val newUrl = apiUrl + "/v1/topics/" + manga.url.trimEnd('/').substringAfterLast("/")
        val response = client.newCall(GET(newUrl)).execute()
        val sManga = mangaDetailsParse(response).apply { initialized = true }
        return Observable.just(sManga)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = JSONObject(response.body!!.string()).getJSONObject("data")
        val manga = SManga.create()
        manga.title = data.getString("title")
        manga.thumbnail_url = data.getString("vertical_image_url")
        manga.author = data.getJSONObject("user").getString("nickname")
        manga.description = data.getString("description")
        manga.status = data.getInt("update_status_code")

        return manga
    }

    // Chapters & Pages

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val newUrl = apiUrl + "/v1/topics/" + manga.url.trimEnd('/').substringAfterLast("/")
        val response = client.newCall(GET(newUrl)).execute()
        val chapters = chapterListParse(response)
        return Observable.just(chapters)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = JSONObject(response.body!!.string()).getJSONObject("data")
        val chaptersJson = data.getJSONArray("comics")
        val chapters = mutableListOf<SChapter>()

        for (i in 0 until chaptersJson.length()) {
            val obj = chaptersJson.getJSONObject(i)
            chapters.add(
                SChapter.create().apply {
                    url = "/v2/comic/" + obj.getString("id")
                    name = obj.getString("title") +
                        if (!obj.getBoolean("can_view")) {
                            " \uD83D\uDD12"
                        } else {
                            ""
                        }
                    date_upload = obj.getLong("created_at") * 1000
                }
            )
        }

        return chapters
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val request = client.newCall(pageListRequest(chapter)).execute()
        return Observable.just(pageListParse(request))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.name.endsWith("🔒")) {
            throw Exception("[此章节为付费内容]")
        }
        return GET(apiUrl + chapter.url)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = ArrayList<Page>()
        val data = JSONObject(response.body!!.string()).getJSONObject("data")
        val pagesJson = data.getJSONArray("images")

        for (i in 0 until pagesJson.length()) {
            pages.add(Page(i, pagesJson.getString(i), pagesJson.getString(i)))
        }
        return pages
    }

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        StatusFilter(),
        GenreFilter()
    )

    override fun imageUrlParse(response: Response): String {
        throw Exception("Not used")
    }

    private class GenreFilter : UriPartFilter(
        "题材",
        arrayOf(
            Pair("全部", "0"),
            Pair("恋爱", "20"),
            Pair("古风", "46"),
            Pair("校园", "47"),
            Pair("奇幻", "22"),
            Pair("大女主", "77"),
            Pair("治愈", "27"),
            Pair("总裁", "52"),
            Pair("完结", "40"),
            Pair("唯美", "58"),
            Pair("日漫", "57"),
            Pair("韩漫", "60"),
            Pair("穿越", "80"),
            Pair("正能量", "54"),
            Pair("灵异", "32"),
            Pair("爆笑", "24"),
            Pair("都市", "48"),
            Pair("萌系", "62"),
            Pair("玄幻", "63"),
            Pair("日常", "19"),
            Pair("投稿", "76")
        )
    )

    private class StatusFilter : UriPartFilter(
        "类别",
        arrayOf(
            Pair("全部", "1"),
            Pair("连载中", "2"),
            Pair("已完结", "3")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        const val TOPIC_ID_SEARCH_PREFIX = "topic:"
    }
}
