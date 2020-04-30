package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Kuaikanmanhua : ParsedHttpSource() {

    override val name = "Kuaikanmanhua"

    override val baseUrl = "https://www.kuaikanmanhua.com"

    override val lang = "zh"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/tag/0?state=1&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseMangaDocument(response.asJsoup())
    }

    private fun parseMangaDocument(document: Document): MangasPage {
        val mangas = mutableListOf<SManga>()

        gson.fromJson<JsonArray>(document.select("script:containsData(datalist)").first().data()
            .substringAfter("dataList:").substringBefore("}],error"))
            .forEach { mangas.add(mangaFromJson(it.asJsonObject)) }

        return MangasPage(mangas, document.select(popularMangaNextPageSelector()).isNotEmpty())
    }

    private fun mangaFromJson(jsonObject: JsonObject): SManga {
        val manga = SManga.create()

        manga.url = "/web/topic/" + jsonObject["id"].asString
        manga.title = jsonObject["title"].asString
        manga.thumbnail_url = jsonObject["cover_image_url"].asString

        return manga
    }

    override fun popularMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = "li:not(.disabled) b.right"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/tag/19?state=1&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            GET("$baseUrl/s/result/$query", headers)
        } else {
            lateinit var genre: String
            lateinit var status: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        genre = filter.toUriPart()
                    }
                    is StatusFilter -> {
                        status = filter.toUriPart()
                    }
                }
            }
            GET("$baseUrl/tag/$genre?state=$status&page=$page", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()

        document.select("script:containsData(resultList)").let {
            return if (it.isNotEmpty()) {
                // for search by query
                gson.fromJson<JsonArray>(it.first().data()
                    .substringAfter("resultList:").substringBefore(",noResult"))
                    .forEach { result -> mangas.add(searchMangaFromJson(result.asJsonObject)) }
                MangasPage(mangas, document.select(searchMangaNextPageSelector()).isNotEmpty())
            } else {
                // for search by genre, status
                parseMangaDocument(document)
            }
        }
    }

    private fun searchMangaFromJson(jsonObject: JsonObject): SManga {
        val manga = SManga.create()

        manga.url = jsonObject["url"].asString
        manga.title = jsonObject["title"].asString
        manga.thumbnail_url = jsonObject["image_url"].asString

        return manga
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.TopicHeader").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h3").first().text()
        manga.author = infoElement.select("div.nickname").text()
        manga.description = infoElement.select("div.detailsBox p").text()

        return manga
    }

    // Chapters & Pages

    override fun chapterListSelector() = "div.TopicItem"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select("div.title a").let {
            chapter.url = it.attr("href")
            chapter.name = it.text() + if (element.select("i.lockedIcon").isNotEmpty()) { " \uD83D\uDD12" } else { "" }
        }
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url == "javascript:void(0);") {
            throw Exception("[此章节为付费内容]")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        gson.fromJson<JsonArray>(document.select("script:containsData(comicImages)").first().data()
            .substringAfter("comicImages:").substringBefore("},nextComicInfo"))
            .forEachIndexed { i, json -> pages.add(Page(i, "", json.asJsonObject["url"].asString)) }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("注意：不影響按標題搜索"),
        StatusFilter(),
        GenreFilter()
    )

    private class GenreFilter : UriPartFilter("题材", arrayOf(
        Pair("全部", "0"),
        Pair("恋爱", "20"),
        Pair("古风", "46"),
        Pair("校园", "47"),
        Pair("奇幻", "22"),
        Pair("大女主", "77"),
        Pair("治愈", "27"),
        Pair("总裁", "52"),
        Pair("完结", "40"),
        Pair("唯美", "58"),
        Pair("日漫", "57"),
        Pair("韩漫", "60"),
        Pair("穿越", "80"),
        Pair("正能量", "54"),
        Pair("灵异", "32"),
        Pair("爆笑", "24"),
        Pair("都市", "48"),
        Pair("萌系", "62"),
        Pair("玄幻", "63"),
        Pair("日常", "19"),
        Pair("投稿", "76")
    ))

    private class StatusFilter : UriPartFilter("类别", arrayOf(
        Pair("全部", "1"),
        Pair("连载中", "2"),
        Pair("已完结", "3")
    ))

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
