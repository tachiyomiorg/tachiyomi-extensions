package eu.kanade.tachiyomi.extension.en.naniscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class NaniScans : HttpSource() {
    override val baseUrl = "https://naniscans.com"
    override val lang = "en"
    override val name = "NANI? Scans"
    override val supportsLatest = true
    override val versionId = 2

    private val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val titlesJson = JSONArray(response.body!!.string())
        val mangaMap = mutableMapOf<Long, SManga>()

        for (i in 0 until titlesJson.length()) {
            val manga = titlesJson.getJSONObject(i)

            if (manga.getString("type") != "Comic")
                continue

            var date = manga.getString("updatedAt")

            if (date == "null")
                date = "2018-04-10T17:38:56"

            mangaMap[dateParser.parse(date)!!.time] = getBareSManga(manga)
        }

        return MangasPage(mangaMap.toSortedMap().values.toList().asReversed(), false)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/titles")

    override fun popularMangaParse(response: Response): MangasPage {
        val titlesJson = JSONArray(response.body!!.string())
        val mangaList = mutableListOf<SManga>()

        for (i in 0 until titlesJson.length()) {
            val manga = titlesJson.getJSONObject(i)

            if (manga.getString("type") != "Comic")
                continue

            mangaList.add(getBareSManga(manga))
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
        val titleJson = JSONObject(response.body!!.string())

        if (titleJson.getString("type") != "Comic")
            throw UnsupportedOperationException("Tachiyomi only supports Comics.")

        return SManga.create().apply {
            title = titleJson.getString("name")
            artist = titleJson.getString("artist")
            author = titleJson.getString("author")
            description = titleJson.getString("synopsis")
            status = getStatus(titleJson.getString("status"))
            thumbnail_url = "$baseUrl${titleJson.getString("coverUrl")}"
            genre = titleJson.getJSONArray("tags").join(", ").replace("\"", "")
            url = titleJson.getString("id")
        }
    }

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api/titles/${manga.url}")

    override fun chapterListParse(response: Response): List<SChapter> {
        val titleJson = JSONObject(response.body!!.string())

        if (titleJson.getString("type") != "Comic")
            throw UnsupportedOperationException("Tachiyomi only supports Comics.")

        val chaptersJson = titleJson.getJSONArray("chapters")
        val chaptersList = mutableListOf<SChapter>()

        for (i in 0 until chaptersJson.length()) {
            val chapter = chaptersJson.getJSONObject(i)

            chaptersList.add(
                SChapter.create().apply {
                    chapter_number = chapter.get("number").toString().toFloat()
                    name = getChapterTitle(chapter)
                    date_upload = dateParser.parse(chapter.getString("releaseDate"))!!.time
                    url = "${titleJson.getString("id")}_${chapter.getString("id")}"
                }
            )
        }

        return chaptersList
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/chapters/${chapter.url.substring(37, 73)}")

    override fun pageListParse(response: Response): List<Page> {
        val jsonObject = JSONObject(response.body!!.string())

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

    private fun getBareSManga(manga: JSONObject): SManga = SManga.create().apply {
        title = manga.getString("name")
        thumbnail_url = "$baseUrl${manga.getString("coverUrl")}"
        url = manga.getString("id")
    }
}
