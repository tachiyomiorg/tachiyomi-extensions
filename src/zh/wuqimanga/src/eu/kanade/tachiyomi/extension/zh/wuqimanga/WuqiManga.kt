package eu.kanade.tachiyomi.extension.zh.wuqimanga

import android.util.Log
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class WuqiManga : ParsedHttpSource() {

    override val name = "57漫画"
    override val baseUrl = "http://www.wuqimh.com"
    override val lang = "zh"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        Log.i("57M", "try to get popular manga")
        throw Exception("Not used")
    }

    override fun popularMangaSelector() = throw Exception("Not used")
    override fun popularMangaNextPageSelector() = throw Exception("Not used")
    override fun popularMangaFromElement(element: Element) = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/", headers)
    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")
    override fun latestUpdatesSelector() = "div.latest-list > ul > li"
    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search/q_$query-p-$page")?.newBuilder()
        Log.i("57M", "search with url $url")
        return GET(url.toString(), headers)
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
            for (element in it.select("dd.tags")) {
                if (element.select("span strong").text().contains("作者")) {
                    manga.author = element.select("span a").text()
                }
            }
        }
        manga.thumbnail_url = element.select("a.bcover > img").attr("src")
        return manga
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun chapterListSelector() = "ul.list_block > li"


    override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)


    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().trim()
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.data")

        val manga = SManga.create()
        manga.description = document.select("div.tbox_js").text().trim()
        manga.author = infoElement.select("p.dir").text().substring(3).trim()
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    private val gson = Gson()

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val baseURLRe = Regex("var z_yurl='(.*?)';")
        val baseImageUrl = baseURLRe.find(html)?.groups?.get(1)?.value

        val re = Regex("var z_img='(.*?)';")
        val imgCode = re.find(html)?.groups?.get(1)?.value
        if (imgCode != null) {
            val anotherStr = gson.fromJson<List<String>>(imgCode)
            return anotherStr.mapIndexed { i, imgStr ->
                Page(i, "", "$baseImageUrl$imgStr")
            }
        }
        return listOf()
    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList())
    )

    private fun getGenreList() = arrayOf(
        "All"
    )
}
