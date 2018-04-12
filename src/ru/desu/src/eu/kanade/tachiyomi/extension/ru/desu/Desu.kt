package eu.kanade.tachiyomi.extension.ru.desu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class Desu : HttpSource() {
    
    override val name = "Desu"

    override val baseUrl = "http://desu.me/manga/api"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi")
        add("Referer", baseUrl)
    }

    private fun mangaPageFromJSON(json: String): MangasPage {
        val arr = JSONArray(json)
        val ret = ArrayList<SManga>(arr.length())
        for (i in 0 until arr.length()) {
            var obj = arr.getJSONObject(i)
            ret.add(SManga.create().apply {
                mangaFromJSON(obj)
            })
        }
        return MangasPage(ret, false)
    }

    private fun SManga.mangaFromJSON(obj: JSONObject) {
        val id = obj.getInt("id")
        url = "/$id"
        title = obj.getString("name")
        thumbnail_url = obj.getJSONObject("image").getString("original")
        description = obj.getString("description")

        status = when (obj.getString("status")) {
            "ongoing" -> SManga.COMPLETED
            "released" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/?limit=50&order=popular&page=1")

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?limit=1&order=updated&page=1")

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?limit=1&order=popular&page=1&search=$query")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        val obj = JSONObject(res).getJSONArray("response")
        return mangaPageFromJSON(obj.toString())
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("response")
        mangaFromJSON(obj)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("response")
        val ret = ArrayList<SChapter>()

        val cid = obj.getInt("id")

        val arr = obj.getJSONObject("chapters").getJSONArray("list")
        for (i in 0 until arr.length()) {
            val obj2 = arr.getJSONObject(i)
            ret.add(SChapter.create().apply {
                val ch = obj2.getString("ch")
                val title = if (obj2.getString("title") == "null") "" else obj2.getString("title")
                name = "$ch - $title"
                val id = obj2.getString("id")
                url = "/$cid/chapter/$id"
            })
        }
        return ret
    }

    override fun pageListParse(response: Response): List<Page> {
        val obj = JSONObject(response.body()!!.string()).getJSONObject("response")
        val pages = obj.getJSONObject("pages")
        val list = pages.getJSONArray("list")
        val ret = ArrayList<Page>(list.length())
        for (i in 0 until list.length()) {
            ret.add(Page(i, "", list.getJSONObject(i).getString("img")))
        }
        return ret
    }

    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList(
    )
}
