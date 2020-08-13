package eu.kanade.tachiyomi.extension.es.plottwistnofansub

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PlotTwistNoFansub : ParsedHttpSource() {

    override val baseUrl = "https://www.plot-twistnf-scans.com"

    override val lang = "es"

    override val name = "Plot Twist No Fansub"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page/?s", headers)

    override fun popularMangaSelector() = "div.td-module-thumb [href*=\"archivos\"]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.attr("title")
            thumbnail_url = it.select("img").attr("src").replace("-150x150", "")
        }
    }

    private fun mangaUrlBuilderFromChapterUrl(chapterUrl: String): String {
        val postName = chapterUrl.substringAfter("read/").substringBefore("/chapter")
        return "/archivos/manga/$postName/"
    }

    override fun popularMangaNextPageSelector() = "div.page-nav a:has(i)"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/actu/", headers)

    override fun latestUpdatesSelector() = "div.row.last-updates div.item"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("div.item").let {
            setUrlWithoutDomain(mangaUrlBuilderFromChapterUrl(it.select("a.lastup").attr("href")))
            title = it.select("a.lastup").text()
            thumbnail_url = it.select("img.lazy.imgigh").attr("data-lazy-src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("article[id^=post-]").let { it ->
            thumbnail_url = it.select("img.entry-thumb").attr("src")
            description = it.select("strong")?.text()
            genre = it.select("div.mangaInfo a").joinToString(", ") {
                it.text()
            }
        }
    }

    override fun chapterListSelector() = throw Exception("Not Used")

    private fun chapterUrlBuilder(postName: String, chapterNumber: String): String {
        return "/read/$postName/chapter-$chapterNumber/"
    }

    private fun paginationFormBuilder(page: Int, mangaId: Int) = FormBody.Builder().apply {
        add("action", "lcap")
        add("manga_id", mangaId.toString())
        add("pageNumber", page.toString())
    }

    private fun parseJson(json: String): JsonElement {
        return JsonParser().parse(json)
    }

    private fun fetchChaptersPertPage(page: Int, mangaId: Int): JsonArray {
        val res = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", headers, paginationFormBuilder(page, mangaId).build())).execute().body()!!.string()
        return parseJson(res.toString()).array
    }

    private fun chapterFromJsonElement(jsonElement: JsonElement) = SChapter.create().apply {
        val nameStr = jsonElement["chapter_name"].string
        val chapterNumber = jsonElement["chapter_number"].string

        name = if (nameStr.contains("Capitulo") || nameStr.contains("Capítulo")) {
            nameStr
        } else if (nameStr.contains("ap&iacute;tulo")) {
            nameStr.replace("&iacute;", "í")
        } else {
            "Capítulo $chapterNumber: $nameStr"
        }

        url = chapterUrlBuilder(jsonElement["post_name"].string, chapterNumber)
        scanlator = "Plot Twist No Fansub"
    }

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        val document = response.asJsoup()
        val mangaId = document.select("link[rel=shortlink]")
            .attr("href")
            .substringAfter("?p=")
            .toIntOrNull()

        if (mangaId != null) {
            var page = 1
            var moreChapters = true

            // This source uses an AJAX paginated plugin to get the chapter list (5 chapters per page),
            // if there are no more chapters it returns [], knowing that:
            var currentPageChaptersList: JsonArray
            val allChaptersList = JsonArray()

            while (moreChapters) {
                currentPageChaptersList = fetchChaptersPertPage(page, mangaId)
                allChaptersList.addAll(currentPageChaptersList)

                if (currentPageChaptersList.size() == 5) page++
                else moreChapters = false
            }

            allChaptersList.forEach {
                add(chapterFromJsonElement(it))
            }
        } else throw Exception("No fue posible obtener la lista capítulos")
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not Used")

    private fun imageUrlBuilder(postName: String, mangaId: String, chapterNumber: String, imgName: String): String {
        return "$baseUrl/wp-manga/${postName}_$mangaId/ch_$chapterNumber/$imgName"
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val jsonStr = document.select("script:containsData(var obj =)")
            .toString()
            .substringAfter("obj =")
            .substringBeforeLast(";").trim()

        val jsonObject = parseJson(jsonStr)
        val imagesList = jsonObject["images"].array
        val mangaPostName = jsonObject["title"].string
        val chapterNumber = jsonObject["actual"].string

        imagesList.forEach {
            add(Page(size, "", imageUrlBuilder(mangaPostName, it["manga_id"].string, chapterNumber, it["image_name"].string)))
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/page/$page/?s=$query")

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()
}
