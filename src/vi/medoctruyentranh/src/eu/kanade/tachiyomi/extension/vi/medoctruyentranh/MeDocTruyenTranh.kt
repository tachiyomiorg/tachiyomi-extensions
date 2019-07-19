package eu.kanade.tachiyomi.extension.vi.medoctruyentranh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class MeDocTruyenTranh : ParsedHttpSource() {

    override val name = "MeDocTruyenTranh"

    override val baseUrl = "http://www.medoctruyentranh.net"

    override val lang = "vi"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = ".morelistCon a"

    override fun searchMangaSelector() = ".listCon a"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/more/${page + 1}", headers)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/$query", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val jsonData = element.ownerDocument().select("#__NEXT_DATA__").first()!!.data()

        manga.setUrlWithoutDomain(baseUrl + element.attr("href"))
        manga.title = element.attr("title").trim()

        val indexOfManga = jsonData.indexOf(manga.title)
        val startIndex = jsonData.indexOf("coverimg", indexOfManga) + 11
        val endIndex = jsonData.indexOf("}", startIndex) - 1
        manga.thumbnail_url = jsonData.substring(startIndex, endIndex)
        return manga
    }

    
    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val jsonData = element.ownerDocument().select("#__NEXT_DATA__").first()!!.data()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.storytitle").text()

        val indexOfManga = jsonData.indexOf(manga.title)
        val startIndex = jsonData.indexOf("coverimg", indexOfManga) + 11
        val endIndex = jsonData.indexOf("}", startIndex) - 1
        manga.thumbnail_url = jsonData.substring(startIndex, endIndex)
        return manga
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val jsonData = JSONObject(document.select("#__NEXT_DATA__").first()!!.data())
        val mangaDetail = jsonData
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("initialState")
                .getJSONObject("detail")
                .getJSONObject("story_item")
        manga.title = mangaDetail.getString("title")
        manga.author = mangaDetail.getJSONArray("author_list").getString(0)
        val genres = mutableListOf<String>()
        for( i in 0 until mangaDetail.getJSONArray("category_list").length()){
            genres.add(mangaDetail.getJSONArray("category_list").getString(i))
        }
        manga.genre = genres.joinToString(", ")
        manga.description = mangaDetail.getString("summary")
        manga.status = parseStatus(mangaDetail.getString("is_updating"))
        manga.thumbnail_url = mangaDetail.getString("coverimg")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("1") -> SManga.ONGOING
        status.contains("0") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.chapters  a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body()!!.string()
        val jsonStringStartIndex = body.indexOf("{\"props\"")
        val jsonStringEndIndex = body.indexOf("</script>", jsonStringStartIndex)
        val jsonString = body.substring(jsonStringStartIndex, jsonStringEndIndex)
        val chapters = mutableListOf<SChapter>()
        val jsonData = JSONObject(jsonString)
        val chaptersArray = jsonData
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("initialState")
                .getJSONObject("detail")
                .getJSONArray("story_chapters")
                .getJSONArray(0)
        val mangaID = jsonData
                .getJSONObject("query")
                .getString("story_id")
        for (i in 0 until chaptersArray.length()) {
            val chapter = SChapter.create()
            val chapterJson = chaptersArray.getJSONObject(i)
            val chapterIndex = chapterJson.getString("chapter_index")
            chapter.setUrlWithoutDomain("$baseUrl/readingPage/$mangaID/$chapterIndex")
            chapter.name = chapterJson.getString("title")
            chapter.date_upload = parseChapterDate(chapterJson.getString("time"))
            chapters.add(chapter)
        }
        return chapters.asReversed()
    }


    private fun parseChapterDate(date: String): Long {
        // 2019-05-09T07:09:58
        val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val dateObject = dateFormat.parse(date)
        return dateObject.time
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val jsonData = JSONObject(document.select("#__NEXT_DATA__").first()?.data() ?: "{}")
        val pagesArray = jsonData
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("initialState")
                .getJSONObject("read")
                .getJSONObject("detail_item")
                .getJSONArray("elements")
        for (i in 0 until pagesArray.length()) {
            pages.add(Page(pages.size, "", pagesArray.getJSONObject(i).getString("content")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("This method should not be called!")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("This method should not be called!")
}