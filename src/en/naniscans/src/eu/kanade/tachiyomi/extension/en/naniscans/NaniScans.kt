package eu.kanade.tachiyomi.extension.en.naniscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable

class NaniScans : HttpSource() {
    override val baseUrl = "https://naniscans.com"
    override val lang = "en"
    override val name = "NANI? Scans"
    override val supportsLatest = true

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val jsonArray = JSONArray(response.body()!!.string())
        val mangaMap = mutableMapOf<Long, SManga>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)

            if (item.getString("type") != "Comic")
                continue

            var date = item.getString("updatedAt")

            if (date == "null")
                date = "2018-04-10T17:38:56"

            mangaMap[SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(date)!!.time] = SManga.create().apply {
                title = item.getString("name")
                url = item.getString("id")
            }
        }

        return MangasPage(mangaMap.toSortedMap().values.toList().asReversed(), false)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/titles")

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonArray = JSONArray(response.body()!!.string())
        val mangaList = mutableListOf<SManga>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)

            if (item.getString("type") != "Comic")
                continue

            mangaList.add(SManga.create().apply {
                title = item.getString("name")
                url = item.getString("id")
            })
        }

        return MangasPage(mangaList, false)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/titles/search?term=$query")

    // Workaround to allow "Open in browser" to use the real URL
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(chapterListRequest(manga)).asObservableSuccess().map { mangaDetailsParse(it).apply { initialized = true } }

    // Return the real URL for "Open in browser"
    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/titles/${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = JSONObject(response.body()!!.string())

        if (jsonObject.getString("type") != "Comic")
            throw UnsupportedOperationException("Tachiyomi only supports Comics.")

        return SManga.create().apply {
            title = jsonObject.getString("name")
            artist = jsonObject.getString("artist")
            author = jsonObject.getString("author")
            description = jsonObject.getString("synopsis")
            status = getStatus(jsonObject.getString("status"))
            thumbnail_url = "$baseUrl${jsonObject.getString("coverUrl")}"
            genre = jsonObject.getJSONArray("tags").join(", ").replace("\"", "")
            url = jsonObject.getString("id")
        }
    }

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api/titles/${manga.url}")

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonObject = JSONObject(response.body()!!.string())

        if (jsonObject.getString("type") != "Comic")
            throw UnsupportedOperationException("Tachiyomi only supports Comics.")

        val chaptersJson = jsonObject.getJSONArray("chapters")
        val chaptersList = mutableListOf<SChapter>()

        for (i in 0 until chaptersJson.length()) {
            val item = chaptersJson.getJSONObject(i)

            chaptersList.add(SChapter.create().apply {
                chapter_number = item.get("number").toString().toFloat()
                name = getChapterTitle(item)
                date_upload = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(item.getString("releaseDate"))!!.time
                url = "/api/titles/${jsonObject.getString("id")}/chapters/${item.getString("id")}"
            })
        }

        return chaptersList
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonObject = JSONObject(response.body()!!.string())

        val pagesJson = jsonObject.getJSONArray("pages")
        val pagesList = mutableListOf<Page>()

        for (i in 0 until pagesJson.length()) {
            val item = pagesJson.getJSONObject(i)

            pagesList.add(Page(item.getInt("number"), "", "$baseUrl${item.getString("pageUrl")}"))
        }

        return pagesList
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not Used.")

    private fun getStatus(status: String): Int = when (status) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun getChapterTitle(chapter: JSONObject): String {
        val chapterName = mutableListOf<String>()

        if (chapter.getString("volume") != "null") {
            chapterName.add("Vol." + chapter.getString("volume"))
        }

        if (chapter.getString("number") != "null") {
            chapterName.add("Ch." + chapter.getString("number"))
        }

        if (chapter.getString("name") != "null") {
            if (chapterName.isNotEmpty()) {
                chapterName.add("-")
            }

            chapterName.add(chapter.getString("name"))
        }

        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }

        return chapterName.joinToString(" ")
    }
}
