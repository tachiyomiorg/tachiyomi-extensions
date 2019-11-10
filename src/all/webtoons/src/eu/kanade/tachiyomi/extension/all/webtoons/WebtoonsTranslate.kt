package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

open class WebtoonsTranslate(override val lang: String, private val translateLangCode: String, private val languageNameExtra: String = "") : Webtoons(lang) {

    private val apiBaseUrl = HttpUrl.parse("https://global.apis.naver.com")!!
    private val mobileBaseUrl = HttpUrl.parse("https://m.webtoons.com")!!
    private val thumbnailBaseUrl = "https://mwebtoon-phinf.pstatic.net"

    private val pageListUrlPattern = "/lineWebtoon/ctrans/translatedEpisodeDetail_jsonp.json?titleNo=%s&episodeNo=%d&languageCode=%s&teamVersion=%d"

    private val pageSize = 24

    override val name = "Webtoons.com Translations${languageNameExtra}"

    override fun headersBuilder() = super.headersBuilder()
        .removeAll("Referer")
        .add("Referer", mobileBaseUrl.toString())

    override fun popularMangaRequest(page: Int): Request {
        // Webtoons translations doesn't really have a "popular" sort; just "UPDATE", "TITLE_ASC",
        // and "TITLE_DESC".  Pick UPDATE as the most useful sort.
        var url = apiBaseUrl
            .resolve("/lineWebtoon/ctrans/translatedWebtoons_jsonp.json")!!
            .newBuilder()
            .addQueryParameter("orderType", "UPDATE")
            .addQueryParameter("offset", "${page * pageSize}")
            .addQueryParameter("size", "${pageSize}")
            .addQueryParameter("languageCode", translateLangCode)
            .build()
        return GET(url.toString(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val responseText = response.body()!!.string()
        val requestURL = response.request().url()
        val offset = requestURL.queryParameter("offset")!!.toInt()
        val responseJSON = JSONObject(responseText)
        val responseCode = responseJSON.getString("code")
        if (responseCode != "000") {
            throw Exception("Error getting popular manga: error code ${responseCode}")
        }
        var results = responseJSON.getJSONObject("result")
        val totalCount = results.getInt("totalCount")
        val titleList = results.getJSONArray("titleList")
        val mangas = (0 until titleList.length()).map { i ->
            val titleJSON = titleList.get(i) as JSONObject
            val titleNo = titleJSON.getInt("titleNo")
            val team = titleJSON.optInt("teamVersion", 0)
            val relativeThumnailURL = titleJSON.getString("thumbnailIPadUrl")
                ?: titleJSON.getString("thumbnailMobileUrl")
            SManga.create()
                .apply {
                    title = titleJSON.getString("representTitle")
                    author = titleJSON.getString("writeAuthorName")
                    artist = titleJSON.getString("pictureAuthorName") ?: author
                    thumbnail_url = if (relativeThumnailURL != null) "${thumbnailBaseUrl}${relativeThumnailURL}" else null
                    status = SManga.UNKNOWN
                    url = mobileBaseUrl
                        .resolve("/translate/episodeList")!!
                        .newBuilder()
                        .addQueryParameter("titleNo", titleNo.toString())
                        .addQueryParameter("languageCode", translateLangCode)
                        .addQueryParameter("teamVersion", team.toString())
                        .build()
                        .toString()
                    initialized = true
                }
        }
        return MangasPage(mangas, totalCount < pageSize * offset)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val getMetaProp = fun(property: String): String =
            document.head().select("meta[property=\"${property}\"]").attr("content")
        var parsedAuthor = getMetaProp("com-linewebtoon:webtoon:author")
        var parsedArtist = parsedAuthor
        val authorSplit = parsedAuthor.split(" / ", limit = 2)
        if (authorSplit.count() > 1) {
            parsedAuthor = authorSplit[0]
            parsedArtist = authorSplit[1]
        }

        return SManga.create().apply {
            title = getMetaProp("og:title")
            artist = parsedArtist
            author = parsedAuthor
            description = getMetaProp("og:description")
            status = SManga.UNKNOWN
            thumbnail_url = getMetaProp("og:image")
        }
    }

    override fun chapterListSelector(): String = throw Exception("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not used")

    override fun chapterListRequest(manga: SManga): Request {
        val titleNo = HttpUrl.parse(manga.url)!!
            .queryParameter("titleNo")
        val chapterUrl = apiBaseUrl
            .resolve("/lineWebtoon/ctrans/translatedEpisodes_jsonp.json")!!
            .newBuilder()
            .addQueryParameter("titleNo", titleNo)
            .addQueryParameter("languageCode", translateLangCode)
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "10000")
            .toString()
        return GET(chapterUrl, mobileHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterData = response.body()!!.string()
        val chapterJson = JSONObject(chapterData)
        val responseCode = chapterJson.getString("code")
        if (responseCode != "000") {
            val message = chapterJson.optString("message", "error code ${responseCode}")
            throw Exception("Error getting chapter list: ${message}")
        }
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
        if (scanlator == "0") {
            scanlator = "(wiki)"
        }
        url = String.format(pageListUrlPattern, obj.getInt("titleNo"), obj.getInt("episodeNo"), obj.getString("languageCode"), obj.getInt("teamVersion"))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(apiBaseUrl.resolve(chapter.url).toString(), headers)
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
