package eu.kanade.tachiyomi.extension.ru.mangaonlinebiz

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.github.salomonbrys.kotson.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class MangaOnlineBiz : ParsedHttpSource() {
    override val name = "MangaOnlineBiz"

    override val baseUrl = "https://manga-online.biz"

    override val lang = "ru"

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/genre/all/page/$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search-ajax/?query=$query", headers)
    }

    override fun popularMangaSelector() = "div.genres a.genre"

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonData = response.body()!!.string()
        val json = JsonParser().parse(jsonData).asJsonObject
        val results = json.getAsJsonArray("results")
        val mangas = mutableListOf<SManga>()
        results.forEach {
            val element = it.asJsonObject
            val manga = SManga.create()
            manga.setUrlWithoutDomain(element.get("url").string)
            manga.title = element.get("title").string.split("/").first()
            manga.thumbnail_url = baseUrl + element.get("image").string
            mangas.add(manga)
        }

        return MangasPage(mangas, false)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first().attr("src")
        manga.setUrlWithoutDomain(element.attr("href"))
        element.select("div.content").first().let {
            manga.title = it.text().split("/").first()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not Used")

    override fun popularMangaNextPageSelector() = "a.ui.button.next"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = throw Exception("Not Used")


    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".items .item").first()
        val manga = SManga.create()
        manga.genre = infoElement.select("a.label").joinToString { it.text() }
        manga.description = infoElement.select(".description").text()
        manga.thumbnail_url = infoElement.select("img").first().attr("src")
        if (infoElement.text().contains("Перевод: закончен")) {
            manga.status = SManga.COMPLETED
        } else if (infoElement.text().contains("Перевод: продолжается")) {
            manga.status = SManga.ONGOING
        }

        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body()!!.string()

        val jsonData = html.split("App.Collection.MangaChapter(").last().split("]);").first() + "]"
        val mangaName = html.split("mangaName: '").last().split("' });").first()
        val json = JsonParser().parse(jsonData).asJsonArray
        val chapterList = mutableListOf<SChapter>()
        json.forEach {
            chapterList.add(chapterFromElement(mangaName, it.asJsonObject))
        }
        return chapterList
    }

    override fun chapterListSelector(): String = throw Exception("Not Used")

    private fun chapterFromElement(mangaName: String, element: JsonObject): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/$mangaName/${element.get("volume").string}/${element.get("number").string})/1")
        chapter.name = "Том ${element.get("volume").string} - Глава ${element.get("number").string} ${element.get("title").string}"
        chapter.chapter_number = element.get("number").float
        chapter.date_upload = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(element.get("date").string).time
        return chapter
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body()!!.string()
        val jsonData = html.split("new App.Router.Chapter(").last().split("});").first() + "}"
        val json = JsonParser().parse(jsonData).asJsonObject
        val cdnUrl = json.get("srcBaseUrl").string
        val pages = json.get("pages").asJsonObject
        val resPages = mutableListOf<Page>()
        pages.forEach { page, jsonElement ->
            resPages.add(Page(page.toInt(), imageUrl = "$cdnUrl/${jsonElement.asJsonObject.get("src").string}"))
        }
        return resPages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun searchMangaSelector(): String = throw Exception("Not Used")

    override fun latestUpdatesSelector(): String = throw Exception("Not Used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not Used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not Used")
}
