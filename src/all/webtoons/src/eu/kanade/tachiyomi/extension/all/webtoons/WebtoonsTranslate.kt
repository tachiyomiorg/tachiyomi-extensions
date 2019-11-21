package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*
import rx.Observable

open class WebtoonsTranslate(override val lang: String, private val translateLangCode: String, languageNameExtra: String = "") : Webtoons(lang) {
    // popularMangaRequest already returns manga sorted by latest update
    override val supportsLatest = false

    private val apiBaseUrl = HttpUrl.parse("https://global.apis.naver.com")!!
    private val mobileBaseUrl = HttpUrl.parse("https://m.webtoons.com")!!
    private val thumbnailBaseUrl = "https://mwebtoon-phinf.pstatic.net"

    private val pageListUrlPattern = "/lineWebtoon/ctrans/translatedEpisodeDetail_jsonp.json?titleNo=%s&episodeNo=%d&languageCode=%s&teamVersion=%d"

    private val pageSize = 24

    override val name = "Webtoons.com Translations$languageNameExtra"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .removeAll("Referer")
        .add("Referer", mobileBaseUrl.toString())

    private fun mangaRequest(page: Int, requeztSize: Int): Request {
        val url = apiBaseUrl
            .resolve("/lineWebtoon/ctrans/translatedWebtoons_jsonp.json")!!
            .newBuilder()
            .addQueryParameter("orderType", "UPDATE")
            .addQueryParameter("offset", "${(page - 1) * requeztSize}")
            .addQueryParameter("size", "$requeztSize")
            .addQueryParameter("languageCode", translateLangCode)
            .build()
        return GET(url.toString(), headers)
    }

    // Webtoons translations doesn't really have a "popular" sort; just "UPDATE", "TITLE_ASC",
    // and "TITLE_DESC".  Pick UPDATE as the most useful sort.
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page, pageSize)

    override fun popularMangaParse(response: Response): MangasPage {
        val offset = response.request().url().queryParameter("offset")!!.toInt()
        var totalCount: Int
        val mangas = mutableListOf<SManga>()

        JSONObject(response.body()!!.string()).let { json ->
            json.getString("code").let { code ->
                if (code != "000") throw Exception("Error getting popular manga: error code $code")
            }

            json.getJSONObject("result").let { results ->
                totalCount = results.getInt("totalCount")

                results.getJSONArray("titleList").let { array ->
                    for (i in 0 until array.length()) {
                        mangas.add(mangaFromJson(array[i] as JSONObject))
                    }
                }
            }
        }

        return MangasPage(mangas, totalCount > pageSize + offset)
    }

    private fun mangaFromJson(json: JSONObject): SManga {
        val relativeThumnailURL = json.getString("thumbnailIPadUrl")
            ?: json.getString("thumbnailMobileUrl")

        return SManga.create().apply {
                title = json.getString("representTitle")
                author = json.getString("writeAuthorName")
                artist = json.getString("pictureAuthorName") ?: author
                thumbnail_url = if (relativeThumnailURL != null) "$thumbnailBaseUrl$relativeThumnailURL" else null
                status = SManga.UNKNOWN
                url = mobileBaseUrl
                    .resolve("/translate/episodeList")!!
                    .newBuilder()
                    .addQueryParameter("titleNo", json.getInt("titleNo").toString())
                    .addQueryParameter("languageCode", translateLangCode)
                    .addQueryParameter("teamVersion", json.optInt("teamVersion", 0).toString())
                    .build()
                    .toString()
                initialized = true
            }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    /**
     * Don't see a search function for Fan Translations, so let's do it client side.
     * There's 75 webtoons as of 2019/11/21, a hardcoded request of 200 should be a sufficient request
     * to get all titles, in 1 request, for quite a while
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaRequest(page, 200)

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val mangas = mutableListOf<SManga>()

        JSONObject(response.body()!!.string()).let { json ->
            json.getString("code").let { code ->
                if (code != "000") throw Exception("Error getting manga: error code $code")
            }

            json.getJSONObject("result").getJSONArray("titleList").let { array ->
                for (i in 0 until array.length()) {
                    (array[i] as JSONObject).let { jsonManga ->
                        if (jsonManga.getString("representTitle").contains(query, ignoreCase = true))
                            mangas.add(mangaFromJson(jsonManga))
                    }
                }
            }
        }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val getMetaProp = fun(property: String): String =
            document.head().select("meta[property=\"$property\"]").attr("content")
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
            val message = chapterJson.optString("message", "error code $responseCode")
            throw Exception("Error getting chapter list: $message")
        }
        val results = chapterJson.getJSONObject("result").getJSONArray("episodes")
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
        val results = pageJson.getJSONObject("result").getJSONArray("imageInfo")
        val ret = ArrayList<Page>()
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            ret.add(Page(i, "", result.getString("imageUrl")))
        }
        return ret
    }

}
