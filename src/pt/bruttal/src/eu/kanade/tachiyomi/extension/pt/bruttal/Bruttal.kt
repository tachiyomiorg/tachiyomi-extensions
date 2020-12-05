package eu.kanade.tachiyomi.extension.pt.bruttal

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class Bruttal : HttpSource() {

    override val name = "Bruttal"

    override val baseUrl = "https://originals.omelete.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/bruttal/")
        .add("User-Agent", USER_AGENT)

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .build()

        return GET("$baseUrl/bruttal/data/home.json", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.asJson().obj

        val titles = json["list"].array.map { jsonEl ->
            popularMangaFromObject(jsonEl.obj)
        }

        return MangasPage(titles, false)
    }

    private fun popularMangaFromObject(obj: JsonObject): SManga = SManga.create().apply {
        title = obj["title"].string
        thumbnail_url = "$baseUrl/bruttal/" + obj["image_mobile"].string.removePrefix("./")
        url = "/bruttal" + obj["url"].string
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map { mp ->
                val filteredTitles = mp.mangas.filter { it.title.contains(query, true) }
                MangasPage(filteredTitles, mp.hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .set("Referer", baseUrl + manga.url)
            .build()

        return GET("$baseUrl/bruttal/data/comicbooks.json", newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.asJson().array

        val titleUrl = response.request().header("Referer")!!.substringAfter("/bruttal")
        val titleObj = json.first { it.obj["url"].string == titleUrl }.obj
        val soonText = titleObj["soon_text"].string

        return SManga.create().apply {
            title = titleObj["title"].string
            thumbnail_url = "$baseUrl/bruttal/" + titleObj["image_mobile"].string.removePrefix("./")
            description = titleObj["synopsis"].string +
                (if (soonText.isEmpty()) "" else "\n\n$soonText")
            artist = titleObj["illustrator"].string
            author = titleObj["author"].string
            genre = titleObj["keywords"].string.replace("; ", ", ")
            status = SManga.ONGOING
        }
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.asJson().array

        val titleUrl = response.request().header("Referer")!!.substringAfter("/bruttal")
        val title = json.first { it.obj["url"].string == titleUrl }.obj

        return title["seasons"].array
            .flatMap { it.obj["chapters"].array }
            .map { jsonEl -> chapterFromObject(jsonEl.obj) }
            .reversed()
    }

    private fun chapterFromObject(obj: JsonObject): SChapter = SChapter.create().apply {
        name = obj["title"].string
        chapter_number = obj["share_title"].string.removePrefix("Capítulo ").toFloatOrNull() ?: -1f
        url = "/bruttal" + obj["url"].string
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .set("Referer", baseUrl + chapter.url)
            .build()

        return GET("$baseUrl/bruttal/data/comicbooks.json", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val json = response.asJson().array

        val chapterUrl = response.request().header("Referer")!!
        val titleSlug = chapterUrl.substringAfter("bruttal/").substringBefore("/")
        val season = chapterUrl.substringAfter("temporada-").substringBefore("/").toInt()
        val chapter = chapterUrl.substringAfter("capitulo-")

        val titleObj = json.first { it.obj["url"].string == "/$titleSlug" }.obj
        val seasonObj = titleObj["seasons"].array[season - 1].obj
        val chapterObj = seasonObj["chapters"].array.first {
            it.obj["alias"].string.substringAfter("-") == chapter
        }

        return chapterObj["images"].array
            .mapIndexed { i, jsonEl ->
                val imageUrl = "$baseUrl/bruttal/" + jsonEl.obj["image"].string.removePrefix("./")
                Page(i, chapterUrl, imageUrl)
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl!!)
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    private fun Response.asJson(): JsonElement = JSON_PARSER.parse(body()!!.string())

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }
    }
}
