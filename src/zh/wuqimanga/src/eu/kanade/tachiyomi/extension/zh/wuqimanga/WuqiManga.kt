package eu.kanade.tachiyomi.extension.zh.wuqimanga

import android.util.Log
import com.github.salomonbrys.kotson.jsonArray
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import com.squareup.duktape.Duktape

class WuqiManga : ParsedHttpSource() {

    override val name = "57漫画"
    override val baseUrl = "http://www.wuqimh.com"
    override val lang = "zh"
    override val supportsLatest = false
    val imageServer = arrayOf("http://images.lancaier.com")

    private fun log(msg: String) = Log.i("tachiyomi", msg)

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")
    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")
    override fun latestUpdatesSelector() = throw Exception("Not used")
    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/area-日本-order-hits", headers)
    override fun popularMangaSelector() = "ul#contList > li"
    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaFromElement(element: Element): SManga {
        val coverEl = element.select("a img").first()
        val cover = if (coverEl.hasAttr("data-src")) {
            coverEl.attr("data-src")
        } else {
            coverEl.attr("src")
        }
        val title = element.select("a").attr("title")
        val url = element.select("a").attr("href")

        val manga = SManga.create()

        manga.thumbnail_url = cover
        manga.title = title
        manga.url = url

        return manga
    }


    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/q_$query-p-$page", headers)
    }

    override fun searchMangaNextPageSelector() = "div.book-result > div > span > a.prev"
    override fun searchMangaSelector() = "div.book-result li.cf"
    override fun searchMangaFromElement(element: Element): SManga {
        Log.i("57M", "try to parse el")
        val manga = SManga.create()
        element.select("div.book-detail").first().let {
            val titleEl = it.select("dl > dt > a")
            manga.setUrlWithoutDomain(titleEl.attr("href"))
            manga.title = titleEl.attr("title").trim()
            manga.description = it.select("dd.intro").text()
            val status = it.select("dd.tags.status")
            manga.status = if (status.select("span.red").first().text().contains("连载中")) {
                SManga.ONGOING
            } else {
                SManga.COMPLETED
            }
            for (el in it.select("dd.tags")) {
                if (el.select("span strong").text().contains("作者")) {
                    manga.author = el.select("span a").text()
                }
            }
        }
        manga.thumbnail_url = element.select("a.bcover > img").attr("src")
        return manga
    }

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/${manga.url}", headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    //    override fun chapterListSelector(): String = "#non-exist-id-23123"
    override fun chapterListSelector() = throw Exception("Not used")

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/${chapter.url}", headers)


    override fun headersBuilder() = Headers.Builder().add("Referer", "$baseUrl/")
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36")


    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.attr("alt").trim()
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        log(document.baseUri())
        val manga = SManga.create()
        manga.description = ""
        manga.title = document.select(".book-title h1").text().trim()
        manga.thumbnail_url = document.select(".hcover img").attr("src")
        for (element in document.select("ul.detail-list li span")) {
            if (element.select("strong").text().contains("漫画作者")) {
                manga.author = element.select("a").text()
                break
            }
        }
        log(gson.toJson(manga))
        return manga
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl.toString(), headers)
    }

    override fun imageUrlRequest(page: Page): Request {
        return GET(page.imageUrl.toString(), headers)
    }

    override fun imageUrlParse(document: Document): String = ""


    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        response.asJsoup().select("div.chapter div.chapter-list>ul").asReversed().forEach {
            it.select("li a").forEach {
                chapters.add(SChapter.create().apply {
                    url = it.attr("href")
                    name = it.attr("title")
                })
            }
        }
        return chapters
    }

    private val gson = Gson()

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val packed = Regex("eval(.*?)\\n").find(html)?.groups?.get(1)?.value
        val result = Duktape.create().use {
            it.evaluate(packed) as String
        }
        val re2 = Regex("""\{.*\}""")
        val imgJsonStr = re2.find(result)?.groups?.get(0)?.value
        val imageJson: Comic = gson.fromJson(imgJsonStr, Comic::class.java)

        return imageJson.fs!!.mapIndexed { i, imgStr ->
            val imgurl = "${imageServer[0]}$imgStr"
            Page(i, "", imgurl)
        }
    }


    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)


    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList())
    )

    private fun getGenreList() = arrayOf(
        "All"
    )
}
