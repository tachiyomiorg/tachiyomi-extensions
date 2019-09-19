package eu.kanade.tachiyomi.extension.en.tsumino

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getArtists
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getDate
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getDesc
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getGroups
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Tsumino: ParsedHttpSource() {

    override val name = "Tsumino"

    override val baseUrl = "https://tsumino.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    override fun latestUpdatesSelector() = "Not needed"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/Search/Operate/?PageNumber=$page&Sort=Newest")

    override fun latestUpdatesParse(response: Response): MangasPage {
    val allManga = mutableListOf<SManga>()
        val jsonManga = gson.fromJson<JsonObject>(response.body()!!.string())["data"].asJsonArray

        for (i in 0 until jsonManga.size()) {
            val manga = SManga.create()
            manga.url = "/entry/" + jsonManga[i]["entry"]["id"].asString
            manga.title = jsonManga[i]["entry"]["title"].asString
            manga.thumbnail_url = jsonManga[i]["entry"]["thumbnailUrl"].asString
            allManga.add(manga)
        }

        return MangasPage(allManga, true)
    }

    override fun latestUpdatesFromElement(element: Element): SManga = throw  UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = "Not needed"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/Search/Operate/?PageNumber=$page&Sort=Popularity")

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw  UnsupportedOperationException("Not implemented yet")

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.book-page-container")
        val manga = SManga.create()
        val genres = mutableListOf<String>()

        infoElement.select("#Tag a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }

        manga.title = infoElement.select("#Title").text()
        manga.artist = getArtists(document)
        manga.author = manga.artist
        manga.status = SManga.COMPLETED
        manga.genre = genres.joinToString(", ")
        manga.thumbnail_url = infoElement.select("img").attr("src")
        manga.description = getDesc(document)

        return manga
    }
    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = mutableListOf<SChapter>()
        val document = response.asJsoup()
        val collection = document.select(chapterListSelector())
        if (collection.isNotEmpty()) {
            return collection.map { element ->
                SChapter.create().apply {
                    name = element.text()
                    scanlator = getGroups(document)
                    setUrlWithoutDomain(element.attr("href").
                        replace("entry", "Read/Index"))
                }
            }.reversed()
        } else {
            val chapter = SChapter.create().apply {
                name = "Chapter"
                scanlator = getGroups(document)
                chapter_number = 1f
                setUrlWithoutDomain(response.request().url().encodedPath().
                    replace("entry", "Read/Index"))
            }
            chapterList.add(chapter)
            return chapterList
        }
    }

    override fun chapterListSelector() = ".book-collection-table a"

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val numPages = document.select("h1").text().split(" ").last()

        if (numPages.isNotEmpty()) {
            for (i in 1 until numPages.toInt() + 1) {
                val data = document.select("#image-container").attr("data-cdn")
                    .replace("[PAGE]", i.toString())
                pages.add(Page(i, "", data))
            }
        } else {
            throw  UnsupportedOperationException("Error: Open in WebView and solve the Captcha!")
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
