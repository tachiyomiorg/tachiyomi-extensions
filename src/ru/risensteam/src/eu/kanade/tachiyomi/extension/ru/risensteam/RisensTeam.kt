package eu.kanade.tachiyomi.extension.ru.risensteam

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable


class RisensTeam : ParsedHttpSource() {
    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")

    override val name = "Risens Team"

    override val baseUrl = "https://risens.team"

    override val lang = "ru"

    override val supportsLatest = false

    private val gson by lazy { Gson() }

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/manga/page/$page/", headers)

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not Used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(), false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not Used")

    override fun popularMangaSelector() = ".mb-2:not([align])"

    override fun latestUpdatesSelector() = throw Exception("Not Used")

    override fun searchMangaSelector() = throw Exception("Not Used")

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val coverElem = element.select(".card-img-top").first()
        val imgUrl = coverElem.attr("src")
        manga.thumbnail_url = baseUrl + if(imgUrl.contains("/pagespeed_static/")) coverElem.attr("data-pagespeed-lazy-src") else imgUrl
        manga.setUrlWithoutDomain(element.select("b-link").first().attr("to"))
        manga.title = coverElem.attr("alt").split('/').first().replace("(Манга)", "").trim()

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not Used")

    override fun searchMangaFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "b-list-group-item.next > a"

    override fun latestUpdatesNextPageSelector() = throw Exception("Not Used")

    override fun searchMangaNextPageSelector() = throw Exception("Not Used")


    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + document.select("b-img[fluid]").first().attr("src")
        manga.genre = document.select("td:containsOwn(Жанр:) + td > a").joinToString { it.ownText() }
        manga.description = document.select(".news-body").text()
        manga.status = when(document.select("td:containsOwn(Состояние:) + td").first().ownText()){
            "Выход продолжается" -> SManga.ONGOING
            "Выход завершён" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun chapterListSelector() = ".related"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val id = document.select("#reader").first().attr("src").split("?id=").last().toInt()
        val jsonData = client.newCall(GET(baseUrl + MANGA_API_URL + id)).execute().body()!!.string()
        val jsonArray = gson.fromJson<List<JsonObject>>(jsonData)
        val chapters = mutableListOf<SChapter>()
        jsonArray.forEach {
            val chapter = SChapter.create()
            chapter.url = CHAPTER_API_URL + it["id"].int
            chapter.name = "Глава ${it["chapter"].string}"
            chapter.chapter_number = it["chapter"].string.toFloat()
            chapters.add(chapter)
        }
        return chapters.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not Used")

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body()!!.string()
        val jsonArray = gson.fromJson<List<String>>(jsonData)
        val pages = mutableListOf<Page>()
        jsonArray.forEachIndexed { index, imageUrl ->
            pages.add(Page(index, imageUrl = imageUrl))
        }
        return pages
    }

    companion object {
        const val MANGA_API_URL = "/risensteam/api/manga.php?id="
        const val CHAPTER_API_URL = "/risensteam/api/chapter.php?id="
    }
}
