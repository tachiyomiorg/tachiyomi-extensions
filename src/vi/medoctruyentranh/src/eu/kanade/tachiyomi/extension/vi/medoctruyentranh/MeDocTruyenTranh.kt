package eu.kanade.tachiyomi.extension.vi.medoctruyentranh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MeDocTruyenTranh : ParsedHttpSource() {

    override val name = "MeDocTruyenTranh"

    override val baseUrl = "https://www.medoctruyentranh.net"

    override val lang = "vi"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = "div.classifyList a"

    override fun searchMangaSelector() = ".listCon a"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/tim-truyen/toan-bo" + if (page > 1) "/$page" else "", headers)
    }

    private inline fun <reified T, R> JSONArray.mapJSONArray(transform: (Int, T) -> R): List<R> {
        val list = mutableListOf<R>()
        for (i in 0 until this.length()) {
            list.add(transform(i, this[i] as T))
        }
        return list
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // trying to build URLs from this JSONObject could cause issues but we need it to get thumbnails
        val titleCoverMap = JSONObject(document.select("script#__NEXT_DATA__").first().data())
            .getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("initialState")
            .getJSONObject("classify")
            .getJSONArray("comics")
            .mapJSONArray { _, jsonObject: JSONObject ->
                Pair(jsonObject.getString("title"), jsonObject.getString("coverimg"))
            }
            .toMap()

        val mangas = document.select(popularMangaSelector()).map {
            popularMangaFromElement(it).apply {
                thumbnail_url = titleCoverMap[this.title]
            }
        }

        return MangasPage(mangas, document.select(popularMangaNextPageSelector()) != null)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/$query", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("div.storytitle").text()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun popularMangaNextPageSelector() = "div.page_floor a.focus + a + a"

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
        for (i in 0 until mangaDetail.getJSONArray("category_list").length()) {
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
        return JSONObject(response.asJsoup().select("script#__NEXT_DATA__").first().data())
            .getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("initialState")
            .getJSONObject("detail")
            .getJSONArray("story_chapters")
            .getJSONArray(0)
            .mapJSONArray { _, jsonObject: JSONObject ->
                SChapter.create().apply {
                    name = jsonObject.getString("title")
                    setUrlWithoutDomain("${response.request().url()}/${jsonObject.getString("chapter_index")}")
                    date_upload = parseChapterDate(jsonObject.getString("time"))
                }
            }
            .reversed()
    }

    private fun parseChapterDate(date: String): Long {
        // 2019-05-09T07:09:58
        val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val dateObject = dateFormat.parse(date)
        return dateObject.time
    }

    override fun pageListParse(document: Document): List<Page> {
        return JSONObject(document.select("#__NEXT_DATA__").first()?.data() ?: "{}")
            .getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("initialState")
            .getJSONObject("read")
            .getJSONObject("detail_item")
            .getJSONArray("elements")
            .mapJSONArray { i, jsonObject: JSONObject ->
                Page(i, "", jsonObject.getString("content"))
            }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("This method should not be called!")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("This method should not be called!")
}
