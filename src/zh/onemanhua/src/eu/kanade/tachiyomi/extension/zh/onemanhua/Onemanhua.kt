package eu.kanade.tachiyomi.extension.zh.onemanhua

import android.util.Base64
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.Error
import java.util.ArrayList
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Onemanhua : ParsedHttpSource() {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "One漫画"
    override val baseUrl = "https://www.onemanhua.com"

    private var decryptKey = "JRUIFMVJDIWE569j"
    private var imageServerUrl = "https://img.onemanhua.com/comic/"

    // Inform server we're using Tachiyomi (Many other ways to detect Tachiyomi other than user-agent)
    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
    }

    // Don't spam the server with your fast fingers
    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    // Common
    private var commonSelector = "li.fed-list-item"
    private var commonNextPageSelector = "a:contains(下页):not(.fed-btns-disad)"
    private fun commonMangaFromElement(element: Element): SManga {
        var picElement = element.select("a.fed-list-pics").first()
        var manga = SManga.create().apply {
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

        var picElement = element.select("a.fed-list-pics").first()
        var manga = SManga.create().apply {
            title = element.select("h1.fed-part-eone a").first().text()
            thumbnail_url = picElement.attr("data-original")
        }

        manga.setUrlWithoutDomain(picElement.attr("href"))

        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        var picElement = document.select("a.fed-list-pics").first()
        var detailElements = document.select("ul.fed-part-rows li.fed-col-xs12")
        var manga = SManga.create().apply {
            title = document.select("h1.fed-part-eone").first().text().trim()
            thumbnail_url = picElement.attr("data-original")
            status = when (detailElements[0].select("a").first().text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = detailElements[1].select("a").first().text()

            genre = detailElements[4].select("a").joinToString{ it.text() }
            description = detailElements[5].select(".fed-part-esan").first().text().trim()
        }

        return manga
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
        var encodedData = getEncodedMangaData(document)
        // 2. decode C_DATA by Base64
        var decodedData = String(Base64.decode(encodedData, Base64.NO_WRAP))
        // 3. decrypt C_DATA
        var decryptedData = decryptAES(decodedData, decryptKey)

        val result = ArrayList<Page>()

        if (decryptedData != null) {
            var imgRelativePath = getImgRelativePath(decryptedData)
            var startImg = getStartImg(decryptedData)
            var totalPages = getTotalPages(decryptedData)

            for(i in startImg..totalPages) {
                result.add(Page(i, "", "${imageServerUrl}${imgRelativePath}${"%04d".format(i)}.jpg"))
            }
        }

        return result
    }

    private fun getEncodedMangaData(document: Document): String? {
        val scriptElements = document.getElementsByTag("script")
        val pattern = Pattern.compile("C_DATA=\'(.+?)\'")
        for (element in scriptElements) {
            if (element.data().contains("C_DATA")) {
                val matcher = pattern.matcher(element.data())
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }
        }

        throw Error("Unable to match for C_DATA")
    }

    private fun decryptAES(value: String, key: String): String? {
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)

        val code = Base64.decode(value, Base64.NO_WRAP)

        return String(cipher.doFinal(code))
    }

    private fun getImgRelativePath(mangaData: String): String {
        val pattern = Pattern.compile("imgpath:\"(.+?)\"")
        var matcher = pattern.matcher(mangaData)
        if (matcher.find()) {
            return matcher.group(1)
        }

        throw Error("Unable to match for imgPath")
    }

    private fun getTotalPages(mangaData: String): Int {
        val pattern = Pattern.compile("totalimg:([0-9]+?),")
        var matcher = pattern.matcher(mangaData)
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1))
        }

        throw Error("Unable to match for totalimg")
    }

    private fun getStartImg(mangaData: String): Int {
        val pattern = Pattern.compile("startimg:([0-9]+?),")
        var matcher = pattern.matcher(mangaData)
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1))
        }

        throw Error("Unable to match for startimg")
    }
}
