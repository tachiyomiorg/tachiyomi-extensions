package eu.kanade.tachiyomi.extension.zh.onemanhua

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Originally, the site was called One漫画. The name has been changing every once in awhile
class Onemanhua : ParsedHttpSource() {
    override val id = 8252565807829914103 // name used to be "One漫画"
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "COCO漫画 (OH漫画)"
    override val baseUrl = "https://www.cocomanhua.com/"

    // Prepend with new decrypt keys (latest keys should appear at the start of the array)
    private var decryptKey1Arr = arrayOf("fw122587mkertyui", "fw12558899ertyui")
    private var decryptKey2Arr = arrayOf("fw125gjdi9ertyui")

    // Common
    private var commonSelector = "li.fed-list-item"
    private var commonNextPageSelector = "a:contains(下页):not(.fed-btns-disad)"
    private fun commonMangaFromElement(element: Element): SManga {
        val picElement = element.select("a.fed-list-pics").first()
        val manga = SManga.create().apply {
            title = element.select("a.fed-list-title").first().text()
            thumbnail_url = picElement.attr("data-original")
        }

        manga.setUrlWithoutDomain(picElement.attr("href"))

        return manga
    }

    // Popular Manga
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show?orderBy=dailyCount&page=$page", headers)
    override fun popularMangaNextPageSelector() = commonNextPageSelector
    override fun popularMangaSelector() = commonSelector
    override fun popularMangaFromElement(element: Element) = commonMangaFromElement(element)

    // Latest Updates
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/show?orderBy=update&page=$page", headers)
    override fun latestUpdatesNextPageSelector() = commonNextPageSelector
    override fun latestUpdatesSelector() = commonSelector
    override fun latestUpdatesFromElement(element: Element) = commonMangaFromElement(element)

    // Filter
    private class StatusFilter : Filter.TriState("已完结")
    private class SortFilter : Filter.Select<String>("排序", arrayOf("更新日", "收录日", "日点击", "月点击"), 2)
    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter()
    )

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search?searchString=$query&page=$page", headers)
        } else {
            val url = HttpUrl.parse("$baseUrl/show")!!.newBuilder()
            url.addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (!filter.isIgnored()) {
                            url.addQueryParameter("status", arrayOf("0", "2", "1")[filter.state])
                        }
                    }
                    is SortFilter -> {
                        url.addQueryParameter("orderBy", arrayOf("update", "create", "dailyCount", "weeklyCount", "monthlyCount")[filter.state])
                    }
                }
            }
            GET(url.toString(), headers)
        }
    }
    override fun searchMangaNextPageSelector() = commonNextPageSelector
    override fun searchMangaSelector() = "dl.fed-deta-info, $commonSelector"
    override fun searchMangaFromElement(element: Element): SManga {
        if (element.tagName() == "li") {
            return commonMangaFromElement(element)
        }

        val picElement = element.select("a.fed-list-pics").first()
        val manga = SManga.create().apply {
            title = element.select("h1.fed-part-eone a").first().text()
            thumbnail_url = picElement.attr("data-original")
        }

        manga.setUrlWithoutDomain(picElement.attr("href"))

        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val picElement = document.select("a.fed-list-pics").first()
        val detailElements = document.select("ul.fed-part-rows li.fed-col-xs12")
        return SManga.create().apply {
            title = document.select("h1.fed-part-eone").first().text().trim()
            thumbnail_url = picElement.attr("data-original")

            status = when (
                detailElements.firstOrNull {
                    it.children().firstOrNull {
                        it2 ->
                        it2.hasClass("fed-text-muted") && it2.ownText() == "状态"
                    } != null
                }?.select("a")?.first()?.text()
            ) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            author = detailElements.firstOrNull {
                it.children().firstOrNull {
                    it2 ->
                    it2.hasClass("fed-text-muted") && it2.ownText() == "作者"
                } != null
            }?.select("a")?.first()?.text()

            genre = detailElements.firstOrNull {
                it.children().firstOrNull {
                    it2 ->
                    it2.hasClass("fed-text-muted") && it2.ownText() == "类别"
                } != null
            }?.select("a")?.joinToString { it.text() }

            description = document.select("ul.fed-part-rows li.fed-col-xs12.fed-show-md-block .fed-part-esan")
                .firstOrNull()?.text()?.trim()
        }
    }

    override fun chapterListSelector(): String = "div:not(.fed-hidden) > div.all_data_list > ul.fed-part-rows a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create().apply {
            name = element.attr("title")
        }
        chapter.setUrlWithoutDomain(element.attr("href"))
        return chapter
    }

    override fun imageUrlParse(document: Document) = ""

    override fun pageListParse(document: Document): List<Page> {
        // 1. get C_DATA from HTML
        val encodedData = getEncodedMangaData(document)
        // 2. decrypt C_DATA
        val decryptedData = decodeAndDecrypt("encodedData", encodedData, decryptKey1Arr)

        // 3. Extract values from C_DATA to formulate page urls
        val imgType = regexExtractStringValue(
            decryptedData,
            "img_type:\"(.+?)\"",
            "Unable to match for img_type"
        )

        return if (imgType.isEmpty()) {
            processPagesFromExternal(decryptedData)
        } else {
            processPagesFromInternal(decryptedData)
        }
    }

    private fun processPagesFromExternal(decryptedData: String): List<Page> {
        val encodedUrlsDirect = regexExtractStringValue(
            decryptedData,
            "urls__direct:\"(.+?)\"",
            "Unable to match for urls__direct"
        )

        val decodedUrlsDirect = String(Base64.decode(encodedUrlsDirect, Base64.NO_WRAP))
        val pageUrlArr = decodedUrlsDirect.split("|SEPARATER|")

        if (pageUrlArr.isNotEmpty()) {
            throw Error("Here ${pageUrlArr[0]} and2 $decodedUrlsDirect")
        }

        return mutableListOf<Page>().apply {
            for (i in pageUrlArr.indices) {
                add(Page(i + 1, "", pageUrlArr[i]))
            }
        }
    }

    private fun processPagesFromInternal(decryptedData: String): List<Page> {
        val imageServerDomain = regexExtractStringValue(
            decryptedData,
            "domain:\"(.+?)\"",
            "Unable to match for imageServerDomain"
        )
        val startImg = regexExtractStringValue(
            decryptedData,
            "startimg:([0-9]+?),",
            "Unable to match for startimg"
        ).let { Integer.parseInt(it) }

        // Decode and decrypt relative path
        val encodedRelativePath = regexExtractStringValue(
            decryptedData,
            "enc_code2:\"(.+?)\"",
            "Unable to match for enc_code2"
        )
        val decryptedRelativePath = decodeAndDecrypt("encodedRelativePath", encodedRelativePath, decryptKey2Arr)

        // Decode and decrypt total pages
        val encodedTotalPages = regexExtractStringValue(
            decryptedData,
            "enc_code1:\"(.+?)\"",
            "Unable to match for enc_code1"
        )
        val decryptedTotalPages = Integer.parseInt(decodeAndDecrypt("encodedTotalPages", encodedTotalPages, decryptKey1Arr))

        return mutableListOf<Page>().apply {
            for (i in startImg..decryptedTotalPages) {
                add(Page(i, "", "https://$imageServerDomain/comic/${encodeUri(decryptedRelativePath)}${"%04d".format(i)}.jpg"))
            }
        }
    }

    private fun getEncodedMangaData(document: Document): String {
        val scriptElements = document.getElementsByTag("script")
        val pattern = Pattern.compile("C_DATA=\'(.+?)\'")
        for (element in scriptElements) {
            if (element.data().contains("C_DATA")) {
                val matcher = pattern.matcher(element.data())
                if (matcher.find()) {
                    val data = matcher.group(1)
                    if (data != null) {
                        return data
                    }
                }
            }
        }

        throw Error("Unable to match for C_DATA")
    }

    private fun decodeAndDecrypt(decodeName: String, value: String, keyArr: Array<String>): String {
        val decodedValue = String(Base64.decode(value, Base64.NO_WRAP))
        for (key in keyArr) {
            try {
                return decryptAES(decodedValue, key)
            } catch (ex: Exception) {
                if (ex.toString() != "Decryption failed") {
                    throw ex
                }
            }
        }

        throw Exception("Decryption failed ($decodeName exhausted keys)")
    }

    @SuppressLint("GetInstance")
    private fun decryptAES(value: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")

        return try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val code = Base64.decode(value, Base64.NO_WRAP)

            String(cipher.doFinal(code))
        } catch (_: Exception) {
            throw Exception("Decryption failed")
        }
    }

    private fun regexExtractStringValue(mangaData: String, regex: String, messageIfError: String): String {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(mangaData)
        if (matcher.find()) {
            return matcher.group(1) ?: throw Exception(messageIfError)
        }

        throw Error(messageIfError)
    }

    /*
    private fun regexExtractIntValue(mangaData: String, regex: String, messageIfError: String): Int {
        return regexExtractStringValue(mangaData, regex, messageIfError).let { Integer.parseInt(it) }
    }
    */

    /*
    private fun encodeUriComponent(str: String): String {
        return URLEncoder.encode(str, "UTF-8")
            .replace("+", "%20")
            .replace("%7E", "~")
            .replace("*", "%2A")
    }
    */

    private fun encodeUri(str: String): String {
        // https://stackoverflow.com/questions/31511922/is-uri-encode-in-android-equivalent-to-encodeuricomponent-in-javascript
        val whitelistChar = "@#&=*+-_.,:!?()/~'%"
        return Uri.encode(str, whitelistChar)
    }
}
