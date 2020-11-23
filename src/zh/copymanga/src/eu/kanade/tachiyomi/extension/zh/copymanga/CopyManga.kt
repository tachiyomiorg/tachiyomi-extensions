package eu.kanade.tachiyomi.extension.zh.copymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CopyManga : HttpSource() {

    override val name = "拷贝漫画"
    override val baseUrl = "https://www.copymanga.com"
    override val lang = "zh"
    override val supportsLatest = true
    private val popularLatestPageSize = 50 // default
    private val searchPageSize = 12 // default

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics?ordering=-popular&offset=${(page - 1) * popularLatestPageSize}&limit=$popularLatestPageSize", headers)
    override fun popularMangaParse(response: Response): MangasPage = parsePopularAndLatestResponse(response)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comics?ordering=-datetime_updated&offset=${(page - 1) * popularLatestPageSize}&limit=$popularLatestPageSize", headers)
    override fun latestUpdatesParse(response: Response): MangasPage = parsePopularAndLatestResponse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var urlString = "$baseUrl/api/kb/web/search/count?format=json&limit=$searchPageSize&offset=${(page - 1) * searchPageSize}&platform=2&q=$query"

        val params = filters.map {
            if (it is ThemeFilter) {
                it.toUriPart()
            } else ""
        }.filter { it != "" }.joinToString("&")
        if (params != "") {
            urlString += "&$params"
        }

        val url = HttpUrl.parse(urlString)?.newBuilder()
        return GET(url.toString(), headers)
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body()!!.string()

        // results > comic > list []
        val res = JSONObject(body)
        val comicArray = res.optJSONObject("results")?.optJSONObject("comic")?.optJSONArray("list")
        if (comicArray == null) {
            return MangasPage(listOf(), false)
        }

        val ret = ArrayList<SManga>(comicArray.length())
        for (i in 0 until comicArray.length()) {
            val obj = comicArray.getJSONObject(i)
            val authorArray = obj.getJSONArray("author")
            ret.add(
                SManga.create().apply {
                    title = obj.getString("name")
                    thumbnail_url = obj.getString("cover")
                    author = Array<String?>(authorArray.length()) { i -> authorArray.getJSONObject(i).getString("name") }.joinToString(", ")
                    status = SManga.UNKNOWN
                    url = "/comic/${obj.getString("path_word")}"
                }
            )
        }

        return MangasPage(ret, comicArray.length() != 0)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val manga = SManga.create().apply {
            thumbnail_url = document.select("div.comicParticulars-title-left img").first().attr("data-src")
            description = document.select("div.comicParticulars-synopsis p.intro").first().text().trim()
        }

        val items = document.select("div.comicParticulars-title-right ul li")
        if (items.size >= 7) {
            manga.author = items[2].select("a").map { i -> i.text().trim() }.joinToString(", ")
            manga.status = when (items[5].select("span.comicParticulars-right-txt").first().text().trim()) {
                "已完結" -> SManga.COMPLETED
                "連載中" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            manga.genre = items[6].select("a").map { i -> i.text().trim() }.joinToString(", ")
        }
        return manga
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val disposableData = document.select("div.disposableData").first().attr("disposable")
        val disposablePass = document.select("div.disposablePass").first().attr("disposable")

        val chapterJsonString = decryptChapterData(disposableData, disposablePass)
        // default > groups > 全部 []
        val chapterJson = JSONObject(chapterJsonString)
        var chapterArray = chapterJson.optJSONObject("default")?.optJSONObject("groups")?.optJSONArray("全部")
        if (chapterArray == null) {
            return listOf()
        }

        val retDefault = ArrayList<SChapter>(chapterArray.length())
        for (i in 0 until chapterArray.length()) {
            val chapter = chapterArray.getJSONObject(i)
            retDefault.add(
                SChapter.create().apply {
                    name = chapter.getString("name")
                    date_upload = stringToUnixTimestamp(chapter.getString("datetime_created")) * 1000
                    url = "/comic/${chapter.getString("comic_path_word")}/chapter/${chapter.getString("uuid")}"
                }
            )
        }

        // {others} > groups > 全部 []
        val retOthers = ArrayList<SChapter>()
        for (categroy in chapterJson.keys()) {
            if (categroy != "default") {
                chapterArray = chapterJson.optJSONObject(categroy)?.optJSONObject("groups")?.optJSONArray("全部")
                if (chapterArray == null) {
                    continue
                }
                for (i in 0 until chapterArray.length()) {
                    val chapter = chapterArray.getJSONObject(i)
                    retOthers.add(
                        SChapter.create().apply {
                            name = chapter.getString("name")
                            date_upload = stringToUnixTimestamp(chapter.getString("datetime_created")) * 1000
                            url = "/comic/${chapter.getString("comic_path_word")}/chapter/${chapter.getString("uuid")}"
                        }
                    )
                }
            }
        }

        // place others to top, as other group updates not so often
        retDefault.addAll(0, retOthers)
        return retDefault.asReversed()
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val disposableData = document.select("div.disposableData").first().attr("disposable")
        val disposablePass = document.select("div.disposablePass").first().attr("disposable")

        val pageJsonString = decryptChapterData(disposableData, disposablePass)
        val pageArray = JSONArray(pageJsonString)

        val ret = ArrayList<Page>(pageArray.length())
        for (i in 0 until pageArray.length()) {
            ret.add(Page(i, "", pageArray.getJSONObject(i).getString("url")))
        }

        return ret
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)
        .add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36 Tachiyomi/1.0")

    // Unused, we can get image urls directly from the chapter page
    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("This method should not be called!")

    // NOT WORK!!! As for now, copymanga has different logic in polular and search page
    override fun getFilterList() = FilterList(
        ThemeFilter(
            "题材",
            "theme",
            arrayOf(
                Pair("全部", "")
            )
        )
    )

    private class ThemeFilter(
        displayName: String,
        searchName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        val searchName = searchName
        open fun toUriPart(): String {
            val selectVal = vals[state].second
            return if (selectVal != "") "$searchName=$selectVal" else ""
        }
    }

    private fun parsePopularAndLatestResponse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.exemptComicList div.exemptComicItem").map { element ->
            mangaFromPage(element)
        }

        // There is always a next pager, so use itemCount to check. XD
        val hasNextPage = mangas.size < popularLatestPageSize

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromPage(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.exemptComicItem-img > a > img").first().let {
            manga.thumbnail_url = it.attr("data-src")
        }
        element.select("div.exemptComicItem-txt > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("p").first().text().trim()
        }
        return manga
    }

    private fun byteArrayToHexString(byteArray: ByteArray): String {
        var sb = ""
        for (b in byteArray) {
            sb += String.format("%02x", b)
        }
        return sb
    }

    private fun hexStringToByteArray(string: String): ByteArray {
        val bytes = ByteArray(string.length / 2)
        for (i in 0 until string.length / 2) {
            bytes[i] = string.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }

    private fun stringToUnixTimestamp(string: String, pattern: String = "yyyy-MM-dd", timeZone: String = "CTT"): Long {
        val date = LocalDate.parse(string, DateTimeFormatter.ofPattern(pattern))
        return date.atStartOfDay(ZoneId.of(timeZone)).toInstant().epochSecond
    }

    // thanks to unpacker toolsite, http://matthewfl.com/unPacker.html
    private fun decryptChapterData(disposableData: String, disposablePass: String = "hotmanga.aes.key"): String {
        val prePart = disposableData.substring(0, 16)
        val postPart = disposableData.substring(16, disposableData.length)
        val disposablePassByteArray = disposablePass.toByteArray(Charsets.UTF_8)
        val prepartByteArray = prePart.toByteArray(Charsets.UTF_8)
        val dataByteArray = hexStringToByteArray(postPart)

        val secretKey = SecretKeySpec(disposablePassByteArray, "AES")
        val iv = IvParameterSpec(prepartByteArray)
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        val result = String(cipher.doFinal(dataByteArray), Charsets.UTF_8)

        return result
    }
}
