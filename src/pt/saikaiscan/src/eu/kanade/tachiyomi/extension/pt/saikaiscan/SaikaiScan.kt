package eu.kanade.tachiyomi.extension.pt.saikaiscan

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SaikaiScan : HttpSource() {

    override val name = "Saikai Scan"

    override val baseUrl = "https://saikaiscan.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("sortProperty", "pageviews")
            .addQueryParameter("sortDirection", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format")
            .toString()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<SaikaiScanPaginatedStoriesDto>(response.body!!.string())

        val mangaList = result.data!!.map(::popularMangaFromObject)
        val hasNextPage = result.meta!!.currentPage < result.meta.lastPage

        return MangasPage(mangaList, hasNextPage)
    }

    private fun popularMangaFromObject(obj: SaikaiScanStoryDto): SManga = SManga.create().apply {
        title = obj.title
        thumbnail_url = "$IMAGE_SERVER_URL/${obj.image}"
        url = "/comics/${obj.slug}"
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/lancamentos".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format,latestReleases.separator")
            .toString()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("q", query)
            .addQueryParameter("sortProperty", "pageViews")
            .addQueryParameter("sortDirection", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format")

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genresParameter = filter.state
                        .filter { it.state }
                        .joinToString(",") { it.id.toString() }
                    apiEndpointUrl.addQueryParameter("genres", genresParameter)
                }

                is CountryFilter -> {
                    if (filter.state > 0) {
                        apiEndpointUrl.addQueryParameter("country", filter.selected.id.toString())
                    }
                }

                is StatusFilter -> {
                    if (filter.state > 0) {
                        apiEndpointUrl.addQueryParameter("status", filter.selected.id.toString())
                    }
                }

                is SortByFilter -> {
                    val sortProperty = filter.sortProperties[filter.state!!.index]
                    val sortDirection = if (filter.state!!.ascending) "asc" else "desc"
                    apiEndpointUrl.setQueryParameter("sortProperty", sortProperty.slug)
                    apiEndpointUrl.setQueryParameter("sortDirection", sortDirection)
                }
            }
        }

        return GET(apiEndpointUrl.toString(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(storyDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun storyDetailsRequest(manga: SManga): Request {
        val storySlug = manga.url.substringAfterLast("/")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("slug", storySlug)
            .addQueryParameter("per_page", "1")
            .addQueryParameter("relationships", "language,type,format,artists,status")
            .toString()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val result = json.decodeFromString<SaikaiScanPaginatedStoriesDto>(response.body!!.string())
        val story = result.data!![0]

        title = story.title
        author = story.authors.joinToString { it.name }
        artist = story.artists.joinToString { it.name }
        thumbnail_url = "$IMAGE_SERVER_URL/${story.image}"
        genre = story.genres.joinToString { it.name }
        status = story.status!!.name.toStatus()
        description = Jsoup.parse(story.synopsis)
            .select("p")
            .joinToString("\n\n") { it.text() }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val storySlug = manga.url.substringAfterLast("/")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("slug", storySlug)
            .addQueryParameter("per_page", "1")
            .addQueryParameter("relationships", "releases")
            .toString()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<SaikaiScanPaginatedStoriesDto>(response.body!!.string())
        val story = result.data!![0]

        return story.releases
            .filter { it.isActive == 1 }
            .map { chapterFromObject(it, story.slug) }
            .sortedByDescending { it.chapter_number }
    }

    private fun chapterFromObject(obj: SaikaiScanReleaseDto, storySlug: String): SChapter =
        SChapter.create().apply {
            name = "Capítulo ${obj.chapter}" +
                (if (obj.title.isNullOrEmpty().not()) " - ${obj.title}" else "")
            chapter_number = obj.chapter.toFloatOrNull() ?: -1f
            date_upload = obj.publishedAt.substringBefore(" ").toDate()
            scanlator = this@SaikaiScan.name
            url = "/ler/comics/$storySlug/${obj.id}/${obj.slug}"
        }

    override fun pageListRequest(chapter: SChapter): Request {
        val releaseId = chapter.url
            .substringBeforeLast("/")
            .substringAfterLast("/")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        val apiEndpointUrl = "$API_URL/api/releases/$releaseId".toHttpUrl().newBuilder()
            .addQueryParameter("relationships", "releaseImages")
            .toString()

        return GET(apiEndpointUrl, apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<SaikaiScanReleaseResultDto>(response.body!!.string())

        return result.data!!.releaseImages.mapIndexed { i, obj ->
            Page(i, "", "$IMAGE_SERVER_URL/${obj.image}")
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    private class Genre(title: String, val id: Int) : Filter.CheckBox(title)

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres)

    private data class Country(val name: String, val id: Int) {
        override fun toString(): String = name
    }

    private open class EnhancedSelect<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
        val selected: T
            get() = values[state]
    }

    private class CountryFilter(countries: List<Country>) : EnhancedSelect<Country>(
        "Nacionalidade",
        countries.toTypedArray()
    )

    private data class Status(val name: String, val id: Int) {
        override fun toString(): String = name
    }

    private class StatusFilter(statuses: List<Status>) : EnhancedSelect<Status>(
        "Status",
        statuses.toTypedArray()
    )

    private data class SortProperty(val name: String, val slug: String) {
        override fun toString(): String = name
    }

    private class SortByFilter(val sortProperties: List<SortProperty>) : Filter.Sort(
        "Ordenar por",
        sortProperties.map { it.name }.toTypedArray(),
        Selection(2, ascending = false)
    )

    // fetch('https://api.saikai.com.br/api/genres')
    //     .then(res => res.json())
    //     .then(res => console.log(res.data.map(g => `Genre("${g.name}", ${g.id})`).join(',\n')))
    private fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", 1),
        Genre("Adulto", 23),
        Genre("Artes Marciais", 84),
        Genre("Aventura", 2),
        Genre("Comédia", 15),
        Genre("Drama", 14),
        Genre("Ecchi", 19),
        Genre("Esportes", 42),
        Genre("eSports", 25),
        Genre("Fantasia", 3),
        Genre("Ficção Cientifica", 16),
        Genre("Histórico", 37),
        Genre("Horror", 27),
        Genre("Isekai", 52),
        Genre("Josei", 40),
        Genre("Luta", 68),
        Genre("Magia", 11),
        Genre("Militar", 76),
        Genre("Mistério", 57),
        Genre("MMORPG", 80),
        Genre("Música", 82),
        Genre("One-shot", 51),
        Genre("Psicológico", 34),
        Genre("Realidade Vitual", 18),
        Genre("Reencarnação", 43),
        Genre("Romance", 9),
        Genre("RPG", 61),
        Genre("Sci-fi", 58),
        Genre("Seinen", 21),
        Genre("Shoujo", 35),
        Genre("Shounen", 26),
        Genre("Slice of Life", 38),
        Genre("Sobrenatural", 74),
        Genre("Suspense", 63),
        Genre("Tragédia", 22),
        Genre("VRMMO", 17),
        Genre("Wuxia", 6),
        Genre("Xianxia", 7),
        Genre("Xuanhuan", 48),
        Genre("Yaoi", 41),
        Genre("Yuri", 83)
    )

    // fetch('https://api.saikai.com.br/api/countries?hasStories=1')
    //     .then(res => res.json())
    //     .then(res => console.log(res.data.map(g => `Country("${g.name}", ${g.id})`).join(',\n')))
    private fun getCountryList(): List<Country> = listOf(
        Country("Todas", 0),
        Country("Brasil", 32),
        Country("China", 45),
        Country("Coréia do Sul", 115),
        Country("Espanha", 199),
        Country("Estados Unidos da América", 1),
        Country("Japão", 109),
        Country("Portugal", 173)
    )

    // fetch('https://api.saikai.com.br/api/countries?hasStories=1')
    //     .then(res => res.json())
    //     .then(res => console.log(res.data.map(g => `Country("${g.name}", ${g.id})`).join(',\n')))
    private fun getStatusList(): List<Status> = listOf(
        Status("Todos", 0),
        Status("Cancelado", 5),
        Status("Concluído", 1),
        Status("Dropado", 6),
        Status("Em Andamento", 2),
        Status("Hiato", 4),
        Status("Pausado", 3)
    )

    private fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty("Título", "title"),
        SortProperty("Quantidade de capítulos", "releases_count"),
        SortProperty("Visualizações", "pageviews"),
        SortProperty("Data de criação", "created_at")
    )

    override fun getFilterList(): FilterList = FilterList(
        CountryFilter(getCountryList()),
        StatusFilter(getStatusList()),
        SortByFilter(getSortProperties()),
        GenreFilter(getGenreList())
    )

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus(): Int = when (this) {
        "Concluído" -> SManga.COMPLETED
        "Em Andamento" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private const val COMIC_FORMAT_ID = "2"
        private const val PER_PAGE = "12"

        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))

        private const val API_URL = "https://api.saikai.com.br"
        private const val IMAGE_SERVER_URL = "https://s3-alpha.saikai.com.br"
    }
}
