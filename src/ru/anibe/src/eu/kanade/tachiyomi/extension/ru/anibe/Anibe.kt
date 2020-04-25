package eu.kanade.tachiyomi.extension.ru.anibe

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.keys
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Anibe : ParsedHttpSource() {

    override val name = "Anibe"

    override val baseUrl = "https://api.anibe.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    // Popular Manga

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/posts?sort=-rating")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val allManga = mutableListOf<SManga>()
        val jsonManga = gson.fromJson<JsonObject>(response.body()!!.string())["rows"].asJsonArray

        for (i in 0 until jsonManga.size()) {
            val manga = SManga.create()
            manga.url = "/posts/" + jsonManga[i]["id"].asString
            manga.title = jsonManga[i]["name"].asString
            manga.thumbnail_url = jsonManga[i]["cover"].asString
            allManga.add(manga)
        }

        return MangasPage(allManga, false)
    }

    override fun popularMangaSelector() = "Unneeded"

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = "Unneeded"

    // Latest Manga

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/posts?sort=-latest")
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search Manga

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/posts?q=$query")
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaSelector() = "Unneeded"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga details

    override fun mangaDetailsParse(response: Response): SManga {
        val jsonManga = gson.fromJson<JsonObject>(response.body()!!.string())

        val manga = SManga.create()
        manga.title = jsonManga["name"].asString
        manga.author = jsonManga["author"].asString
        val status = jsonManga["status"].asString
        manga.status = parseStatus(status)
        manga.genre = jsonManga["genre"].toString().substringAfter("[").substringBefore("]").replace("\"", "").replace(",", " ")
        manga.description = jsonManga["description"].asString
        manga.thumbnail_url = jsonManga["cover"].asString
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException("Not used")

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Продолжается") -> SManga.ONGOING
        status.contains("Завершен") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapter parsing

    /* Technically we would get the same response for chapter.url with or without the ?$i below
       However, if all the chapters for a manga have the same URL, every chapter will have the same pages
       So, add an "unnecessary" incremented tag that doesn't break the URL but still adds uniqueness */
    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        val jsonManga = gson.fromJson<JsonObject>(response.body()!!.string())
        val jsonChapters = jsonManga["episodes"].asJsonObject.keys().sortedWith(Comparator<String> { a, b ->
            when {
                a.substringBefore("_").toInt() < b.substringBefore("_").toInt() -> 1
                a.substringBefore("_").toInt() > b.substringBefore("_").toInt() -> -1
                a.substringAfter("_") < b.substringAfter("_") -> 1
                a.substringAfter("_") > b.substringAfter("_") -> -1
                else -> 0
            }
        })

        for (i in 0 until jsonChapters.size) {
            val chapter = SChapter.create()
            chapter.name = "Chapter: " + jsonChapters.elementAt(i) // Key for chapter in JSON object
            chapter.url = "/posts/" + jsonManga["id"].asString + "?$i"
            allChapters.add(chapter)
        }
        return allChapters
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    private var chNum = ""
    override fun pageListRequest(chapter: SChapter): Request {
        chNum = chapter.name.substringAfter("Chapter: ") // This is how we get the chapter key for pageListParse
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonPages = gson.fromJson<JsonObject>(response.body()!!.string())["episodes"][chNum].asJsonArray
        val pages = mutableListOf<Page>()

        for (i in 0 until jsonPages.size()) {
            pages.add(Page(i, "", jsonPages[i].asString))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
