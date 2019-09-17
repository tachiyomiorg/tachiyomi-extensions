package eu.kanade.tachiyomi.extension.all.webtoons

import android.util.Log.e
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

open class WebtoonsTranslate(override val lang: String, private val translationLangCode: String) : Webtoons(lang) {

    private val apiBaseUrl = "https://global.apis.naver.com"

    private val chapterListUrlPattern = "/lineWebtoon/ctrans/translatedEpisodes_jsonp.json?titleNo=%d&languageCode=%s&offset=0&limit=10000"

    private val pageListUrlPattern = "/lineWebtoon/ctrans/translatedEpisodeDetail_jsonp.json?titleNo=%s&episodeNo=%d&languageCode=%s&teamVersion=%d"

    override fun chapterListSelector(): String = throw Exception("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

    override fun chapterListRequest(manga: SManga): Request {
        val original = manga.url;
        val titleRegex = Regex("title_?[nN]o=([0-9]*)")
        val titleNo = titleRegex.find(original)!!.groupValues[1].toInt()

        val chapterUrl = String.format("$apiBaseUrl$chapterListUrlPattern", titleNo, translationLangCode)
        return GET(chapterUrl, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterJson = JSONObject(response.body()!!.string())
        var results = chapterJson.getJSONObject("result").getJSONArray("episodes")
        val ret = ArrayList<SChapter>()
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            if (result.getBoolean("translateCompleted")) {
                ret.add(parseChapterJson(result))
            }
        }
        ret.reverse()
        return ret
    }

    private fun parseChapterJson(obj: JSONObject) = SChapter.create().apply {
        name = obj.getString("title") + " #" + obj.getString("episodeSeq")
        chapter_number = obj.getInt("episodeSeq").toFloat()
        date_upload = obj.getLong("updateYmdt")
        scanlator = obj.getString("teamVersion")
        url = String.format(pageListUrlPattern, obj.getInt("titleNo"), obj.getInt("episodeNo"), obj.getString("languageCode"), obj.getInt("teamVersion"))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(apiBaseUrl + chapter.url, mobileHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageJson = JSONObject(response.body()!!.string())
        var results = pageJson.getJSONObject("result").getJSONArray("imageInfo")
        val ret = ArrayList<Page>()
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            ret.add(Page(i, "", result.getString("imageUrl")))
        }
        return ret
    }

}
