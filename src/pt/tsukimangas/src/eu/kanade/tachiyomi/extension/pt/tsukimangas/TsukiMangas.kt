package eu.kanade.tachiyomi.extension.pt.tsukimangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.annotations.Nsfw
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class TsukiMangas : HttpSource() {

    override val name = "Tsuki Mangás"

    override val baseUrl = "https://tsukimangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/v2/mangas?page=$page&title=&filter=0", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJson().obj

        val popularMangas = result["data"].array
            .map { popularMangaItemParse(it.obj) }

        val hasNextPage = result["page"].int < result["lastPage"].int
        return MangasPage(popularMangas, hasNextPage)
    }

    private fun popularMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["title"].string
        thumbnail_url = baseUrl + "/imgs/" + obj["poster"].string.substringBefore("?")
        url = "/obra/${obj["id"].int}/${obj["url"].string}"
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/v2/home/lastests?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asJson().obj

        val latestMangas = result["data"].array
            .map { latestMangaItemParse(it.obj) }

        val hasNextPage = result["page"].int < result["lastPage"].int
        return MangasPage(latestMangas, hasNextPage)
    }

    private fun latestMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["title"].string
        thumbnail_url = baseUrl + "/imgs/" + obj["poster"].string.substringBefore("?")
        url = "/obra/${obj["id"].int}/${obj["url"].string}"
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/lista-completa")
            .build()

        val url = "$baseUrl/api/v2/mangas?page=$page".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("title", query)

        // Genre filter must be the first.
        filters.filterIsInstance<GenreFilter>().firstOrNull()?.state
            ?.filter { it.state }
            ?.forEach { url.addQueryParameter("genres[]", it.name) }

        // Sort by filter must also be the first.
        filters.filterIsInstance<SortByFilter>().firstOrNull()
            ?.let { filter ->
                if (filter.state!!.index == 0) {
                    url.addQueryParameter("filter", if (filter.state!!.ascending) "1" else "0")
                } else {
                    url.addQueryParameter("filter", if (filter.state!!.ascending) "3" else "2")
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is DemographyFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("demography", filter.state.toString())
                    }
                }

                is FormatFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("format", filter.state.toString())
                    }
                }

                is StatusFilter -> {
                    if (filter.state > 0) {
                        url.addQueryParameter("status", (filter.state - 1).toString())
                    }
                }

                is AdultFilter -> {
                    if (filter.state == Filter.TriState.STATE_INCLUDE) {
                        url.addQueryParameter("adult_content", "1")
                    } else if (filter.state == Filter.TriState.STATE_EXCLUDE) {
                        url.addQueryParameter("adult_content", "false")
                    }
                }
            }
        }

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asJson().obj

        val searchResults = result["data"].array
            .map { searchMangaItemParse(it.obj) }

        val hasNextPage = result["page"].int < result["lastPage"].int

        return MangasPage(searchResults, hasNextPage)
    }

    private fun searchMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["title"].string
        thumbnail_url = baseUrl + "/imgs/" + obj["poster"].string.substringBefore("?")
        url = "/obra/${obj["id"].int}/${obj["url"].string}"
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

        val mangaId = manga.url.substringAfter("obra/").substringBefore("/")

        return GET("$baseUrl/api/v2/mangas/$mangaId", newHeaders)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .removeAll("Accept")
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asJson().obj

        return SManga.create().apply {
            title = result["title"].string
            thumbnail_url = baseUrl + "/imgs/" + result["poster"].string.substringBefore("?")
            description = result["synopsis"].nullString.orEmpty()
            status = result["status"].nullString.orEmpty().toStatus()
            author = result["author"].nullString.orEmpty()
            artist = result["artist"].nullString.orEmpty()
            genre = result["genres"].array.joinToString { it.obj["genre"].string }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfter("obra/").substringBefore("/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + manga.url)
            .build()

        return GET("$baseUrl/api/v2/chapters/$mangaId/all", newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.header("Referer")!!.substringAfter(baseUrl)

        return response.asJson().array
            .flatMap { chapterListItemParse(it.obj, mangaUrl) }
            .reversed()
    }

    private fun chapterListItemParse(obj: JsonObject, mangaUrl: String): List<SChapter> {
        val mangaId = mangaUrl.substringAfter("obra/").substringBefore("/")
        val mangaSlug = mangaUrl.substringAfterLast("/")

        return obj["versions"].array.map { version ->
            SChapter.create().apply {
                name = "Cap. " + obj["number"].string +
                    (if (!obj["title"].nullString.isNullOrEmpty()) " - " + obj["title"].string else "")
                chapter_number = obj["number"].string.toFloatOrNull() ?: -1f
                scanlator = version.obj["scans"].array
                    .sortedBy { it.obj["scan"].obj["name"].string }
                    .joinToString { it.obj["scan"].obj["name"].string }
                date_upload = version.obj["created_at"].string.substringBefore(" ").toDate()
                url = "/leitor/$mangaId/${version.obj["id"].int}/$mangaSlug/${obj["number"].string}"
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url)
            .build()

        val mangaId = chapter.url
            .substringAfter("leitor/")
            .substringBefore("/")
        val versionId = chapter.url
            .substringAfter("$mangaId/")
            .substringBefore("/")

        return GET("$baseUrl/api/v2/chapter/versions/$versionId", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asJson().obj

        return result["pages"].array.mapIndexed { i, page ->
            val server = page["server"].string
            val cdnUrl = "https://cdn$server.tsukimangas.com"
            Page(i, "$baseUrl/", cdnUrl + page.obj["url"].string)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Accept-Language", ACCEPT_LANGUAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class Genre(name: String) : Filter.CheckBox(name)

    private class DemographyFilter(demographies: List<String>) : Filter.Select<String>("Demografia", demographies.toTypedArray())

    private class FormatFilter(types: List<String>) : Filter.Select<String>("Formato", types.toTypedArray())

    private class StatusFilter(statusList: List<String>) : Filter.Select<String>("Status", statusList.toTypedArray())

    private class AdultFilter : Filter.TriState("Conteúdo adulto")

    private class SortByFilter : Filter.Sort("Ordenar por", arrayOf("Visualizações", "Nota"), Selection(0, false))

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres)

    override fun getFilterList(): FilterList = FilterList(
        DemographyFilter(getDemographiesList()),
        FormatFilter(getSerieFormats()),
        StatusFilter(getStatusList()),
        AdultFilter(),
        SortByFilter(),
        GenreFilter(getGenreList()),
    )

    private fun getDemographiesList(): List<String> = listOf(
        "Todas",
        "Shounen",
        "Shoujo",
        "Seinen",
        "Josei"
    )

    private fun getSerieFormats(): List<String> = listOf(
        "Todos",
        "Mangá",
        "Manhwa",
        "Manhua",
        "Novel"
    )

    private fun getStatusList(): List<String> = listOf(
        "Todos",
        "Ativo",
        "Completo",
        "Cancelado",
        "Hiato"
    )

    // [...document.querySelectorAll(".multiselect:first-of-type .multiselect__element span span")]
    //     .map(i => `Genre("${i.innerHTML}")`).join(",\n")
    private fun getGenreList(): List<Genre> = listOf(
        Genre("4-Koma"),
        Genre("Adaptação"),
        Genre("Aliens"),
        Genre("Animais"),
        Genre("Antologia"),
        Genre("Artes Marciais"),
        Genre("Aventura"),
        Genre("Ação"),
        Genre("Colorido por fã"),
        Genre("Comédia"),
        Genre("Crime"),
        Genre("Cross-dressing"),
        Genre("Deliquentes"),
        Genre("Demônios"),
        Genre("Doujinshi"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Esportes"),
        Genre("Fantasia"),
        Genre("Fantasmas"),
        Genre("Filosófico"),
        Genre("Gals"),
        Genre("Ganhador de Prêmio"),
        Genre("Garotas Monstro"),
        Genre("Garotas Mágicas"),
        Genre("Gastronomia"),
        Genre("Gore"),
        Genre("Harém"),
        Genre("Harém Reverso"),
        Genre("Hentai"),
        Genre("Histórico"),
        Genre("Horror"),
        Genre("Incesto"),
        Genre("Isekai"),
        Genre("Jogos Tradicionais"),
        Genre("Lolis"),
        Genre("Long Strip"),
        Genre("Mafia"),
        Genre("Magia"),
        Genre("Mecha"),
        Genre("Medicina"),
        Genre("Militar"),
        Genre("Mistério"),
        Genre("Monstros"),
        Genre("Música"),
        Genre("Ninjas"),
        Genre("Obscenidade"),
        Genre("Oficialmente Colorido"),
        Genre("One-shot"),
        Genre("Policial"),
        Genre("Psicológico"),
        Genre("Pós-apocalíptico"),
        Genre("Realidade Virtual"),
        Genre("Reencarnação"),
        Genre("Romance"),
        Genre("Samurais"),
        Genre("Sci-Fi"),
        Genre("Shotas"),
        Genre("Shoujo Ai"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Sobrenatural"),
        Genre("Sobrevivência"),
        Genre("Super Herói"),
        Genre("Thriller"),
        Genre("Todo Colorido"),
        Genre("Trabalho de Escritório"),
        Genre("Tragédia"),
        Genre("Troca de Gênero"),
        Genre("Vampiros"),
        Genre("Viagem no Tempo"),
        Genre("Vida Escolar"),
        Genre("Violência Sexual"),
        Genre("Vídeo Games"),
        Genre("Webcomic"),
        Genre("Wuxia"),
        Genre("Yaoi"),
        Genre("Yuri"),
        Genre("Zumbis")
    )

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus() = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun Response.asJson(): JsonElement = JsonParser.parseString(body!!.string())

    companion object {
        private const val ACCEPT = "application/json, text/plain, */*"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        // By request of site owner. Detailed at Issue #4912 (in Portuguese).
        private val USER_AGENT = "Tachiyomi " + System.getProperty("http.agent")

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
