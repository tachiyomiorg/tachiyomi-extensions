package eu.kanade.tachiyomi.extension.en.wecomics

import android.util.Base64
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import org.xxtea.XXTEA
import rx.Observable
import java.net.URLEncoder

class WeComics : HttpSource() {

    override val name = "WeComics"

    override val baseUrl = "https://m.wecomics.com"

    override val lang = "en"

    override val supportsLatest = true

    private val gson = Gson()

    private fun getMangaId(url: String): String? =
        Regex("""^/comic/index/id/\d+\?id=(\d+)""").find(url)?.groupValues?.get(1)

    private fun getChapterId(url: String): Pair<String, String> {
        val pattern = Regex("""^/chapter/index\?id=(\d+)&cid=(\d+)""")
        val matches = pattern.find(url)?.groupValues!!
        return Pair(matches[1], matches[2])
    }

    private fun Int.toStatus() = when (this) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/h5/rank/getAllComicList/page/$page?plain=1")

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        val mangas = jsonObject["data"]["comic_list"].asJsonArray.map {
            SManga.create().apply {
                url = "/comic/index/id/${it["comic_id"].asInt}?id=${it["comic_id"].asInt}"
                title = it["title"].asString
                author = it["artist_name"][0].asString.split(",，").joinToString()
                description = it["brief_intrd"].asString
                genre = it["tag"].asJsonArray.joinToString { it["name"].asString }
                status = it["finish_state"].asInt.toStatus()
                thumbnail_url = it["cover_v_url"].asString
            }
        }
        return MangasPage(mangas, jsonObject["data"]["has_next_page"].asInt == 1)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/h5/rank/getNewComicList/page/$page?plain=1", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queryEncoded = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/h5/search/smart/word/$queryEncoded?plain=1", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        return MangasPage(
            jsonObject["data"].asJsonArray.map {
                SManga.create().apply {
                    url = "/comic/index/id/${it["comic_id"].asInt}?id=${it["comic_id"].asInt}"
                    title = it["title"].asString
                    author = it["artist_name"][0].asString.split(",，").joinToString()
                    status = SManga.UNKNOWN
                    thumbnail_url = it["cover_v_url"].asString
                }
            },
            false
        )
    }

    // Details

    // mangaDetailsRequest is used for WebView
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    // For WebView
    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("${baseUrl}${manga.url}&type=search", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())

        val it = jsonObject["data"]["comic"].asJsonObject
        return SManga.create().apply {
            url = "/comic/index/id/${it["comic_id"].asInt}?id=${it["comic_id"].asInt}"
            title = it["title"].asString
            author = it["artist_name"][0].asString.split(",，").joinToString()
            description = it["brief_intrd"].asString
            genre = it["tag"].asJsonArray.joinToString { it["name"].asString }
            status = it["finish_state"].asInt.toStatus()
            thumbnail_url = it["cover_v_url"].asString
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request =
        GET("https://m.wecomics.com/h5/comic/detail/id/${getMangaId(manga.url)}?plain=1", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())
        val mangaId = jsonObject["data"]["comic"]["comic_id"].asInt

        return jsonObject["data"]["chapter_list"].asJsonArray.map {
            SChapter.create().apply {
                url = "/chapter/index?id=$mangaId&cid=${it["chapter_id"]}"
                name = it["title"].asString
                date_upload = it["publish_time"].asLong * 1000
                chapter_number = it["seq_no"].asFloat
                if (it["vip_state"].asInt == 2) scanlator = "Premium"
            }
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val (mangaId, chapterId) = getChapterId(chapter.url)
        return GET("$baseUrl/h5/comic/getPictureList/id/$mangaId/cid/$chapterId?plain=1", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request().url().toString()

        // Error code 401 when not logged in and data is empty when logged in,
        // assuming this is populated after a purchase
        val jsonObject = gson.fromJson<JsonObject>(response.body()!!.string())
        if (jsonObject["error_code"].asInt != 2 &&
            jsonObject["data"]["chapter"]["data"].asString != ""
        )
            throw Exception("Chapter is currently not available.")

        val data = jsonObject["data"]["chapter"]["data"].asString
        val key = data.substring(0, 8)
        val encrypted = Base64.decode(data.substring(8), Base64.DEFAULT)
        val chData = XXTEA.decryptToString(encrypted, key)

        val jsonObjectInner = gson.fromJson<JsonObject>(chData)
        val cdnUrl = jsonObjectInner["cdn_base_url"].asString

        // The inner JSON contains a list of parts of files,
        // the parts appear to be split at a fixed size
        return jsonObjectInner["picture_list"].asJsonArray.mapIndexed { i, it ->
            Page(i, url, cdnUrl + it["picture_url"].asString)
        }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")
}
