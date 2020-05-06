package eu.kanade.tachiyomi.extension.pt.tsukimangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class TsukiMangas : HttpSource() {

    override val name = "Tsuki Mangás"

    override val baseUrl = "https://tsukimangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(150, 1, TimeUnit.MINUTES)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(rateLimitInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", "application/json, text/plain, */*")
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/melhores", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJson().array

        val popularMangas = result.map { popularMangaItemParse(it.obj) }

        return MangasPage(popularMangas, false)
    }

    private fun popularMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["TITULO"].string
        thumbnail_url = baseUrl + "/imgs/" + obj["CAPA"].string.substringBefore("?")
        url = "/manga/" + obj["URL"].string
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/lancamentos/$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val json = response.asJson().array

        if (json.size() == 0)
            return MangasPage(emptyList(), false)

        val result = json[0].obj

        val latestMangas = result["mangas"].array
            .map { latestMangaItemParse(it.obj) }

        // Latest pagination doesn't seen to have a lower end.
        return MangasPage(latestMangas, true)
    }

    private fun latestMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["TITULO"].string
        thumbnail_url = baseUrl + "/imgs/" + obj["CAPA"].string
        url = "/manga/" + obj["URL"].string
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response -> searchMangaParse(response) }
            .onErrorReturn {
                if (it.message!!.contains("404")) {
                    return@onErrorReturn MangasPage(emptyList(), false)
                }

                throw it
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/lista-mangas")
            .build()

        val pathQuery = if (query.isEmpty()) "all" else query

        val genreFilter = if (filters.isEmpty()) null else filters[0] as GenreFilter
        val genreQuery = genreFilter?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.name } ?: "all"

        val url = HttpUrl.parse("$baseUrl/api/generos")!!.newBuilder()
            .addEncodedPathSegment(genreQuery)
            .addPathSegment(page.toString())
            .addEncodedPathSegment(pathQuery)
            .toString()

        return GET(url, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJson().array

        if (result.size() == 0)
            return MangasPage(emptyList(), false)

        val searchMangas = result.map { searchMangaItemParse(it.obj) }

        val currentPage = response.request().url().toString()
            .substringBeforeLast("/")
            .substringAfterLast("/")
            .toInt()
        val lastPage = result[0].obj["page"].array[0].int
        val hasNextPage = currentPage < lastPage

        return MangasPage(searchMangas, hasNextPage)
    }

    private fun searchMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["TITULO"].string
        thumbnail_url = baseUrl + "/imgs/" + obj["CAPA"].string.substringBefore("?")
        url = "/manga/" + obj["URL"].string
    }

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
            .set("Referer", baseUrl + manga.url)
            .build()

        val mangaSlug = manga.url.substringAfterLast("/")

        return GET("$baseUrl/api/mangas/$mangaSlug", newHeaders)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .removeAll("Accept")
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asJson().array[0].obj

        return SManga.create().apply {
            title = result["TITULO"].string
            thumbnail_url = baseUrl + "/imgs/" + result["CAPA"].string.substringBefore("?")
            description = result["SINOPSE"].string
            status = SManga.ONGOING
            author = result["AUTOR"].string
            artist = result["ARTISTA"].string
            genre = result["GENEROS"].string
        }
    }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequestPaginated(manga.url, 1)

    private fun chapterListRequestPaginated(mangaUrl: String, page: Int): Request {
        val slug = mangaUrl.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET("$baseUrl/api/capitulospag/$slug/DESC/$page", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var result = response.asJson().array

        if (result.size() == 0)
            return emptyList()

        val mangaUrl = response.request().header("Referer")!!.substringAfter(baseUrl)
        val mangaSlug = mangaUrl.substringAfterLast("/")
        var page = 1

        val chapters = mutableListOf<SChapter>()

        while (result.size() != 0) {
            chapters += result
                .map { chapterListItemParse(it.obj, mangaSlug) }
                .toMutableList()

            val newRequest = chapterListRequestPaginated(mangaUrl, ++page)
            result = client.newCall(newRequest).execute().asJson().array
        }

        return chapters
    }

    private fun chapterListItemParse(obj: JsonObject, slug: String): SChapter = SChapter.create().apply {
        name = "Cap. " + obj["NUMERO"].string +
            (if (obj["TITULO"].string.isNotEmpty()) " - " + obj["TITULO"].string else "")
        chapter_number = obj["NUMERO"].string.toFloatOrNull() ?: 0f
        scanlator = obj["scans"].array.joinToString { it.obj["NOME"].string }
        date_upload = DATE_FORMATTER.tryParseDate(obj["DATA"].string.substringBefore("T"))
        url = "/leitor/$slug/" + obj["NUMERO"].string
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url)
            .build()

        return GET("$baseUrl/api" + chapter.url, newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asJson().array
        val chapterUrl = response.request().header("Referer")!!

        return result.mapIndexed { i, page -> Page(i, chapterUrl, page.obj["IMG"].string) }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class Genre(name: String) : Filter.CheckBox(name)

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres)

    override fun getFilterList(): FilterList = FilterList(GenreFilter(getGenreList()))

    // [...document.querySelectorAll(".multiselect__element span span")]
    //     .map(i => `Genre("${i.innerHTML}")`).join(",\n")
    private fun getGenreList(): List<Genre> = listOf(
        Genre("4-koma"),
        Genre("Adulto"),
        Genre("Artes Marciais"),
        Genre("Aventura"),
        Genre("Ação"),
        Genre("Bender"),
        Genre("Comédia"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Esporte"),
        Genre("Fantasia"),
        Genre("Ficção"),
        Genre("Gastronomia"),
        Genre("Gender"),
        Genre("Guerra"),
        Genre("Harém"),
        Genre("Histórico"),
        Genre("Horror"),
        Genre("Isekai"),
        Genre("Josei"),
        Genre("Magia"),
        Genre("Manhua"),
        Genre("Manhwa"),
        Genre("Mecha"),
        Genre("Medicina"),
        Genre("Militar"),
        Genre("Mistério"),
        Genre("Musical"),
        Genre("One-Shot"),
        Genre("Psicológico"),
        Genre("Romance"),
        Genre("Sci-fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Sobrenatural"),
        Genre("Super Poderes"),
        Genre("Suspense"),
        Genre("Terror"),
        Genre("Thriller"),
        Genre("Tragédia"),
        Genre("Vida Escolar"),
        Genre("Webtoon"),
        Genre("Yaoi"),
        Genre("Yuri"),
        Genre("Zumbi")
    )

    private fun SimpleDateFormat.tryParseDate(date: String): Long {
        return try {
            parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun Response.asJson(): JsonElement = JSON_PARSER.parse(body()!!.string())

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
