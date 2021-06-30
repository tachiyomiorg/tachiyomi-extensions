package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.SpecificHostRateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Hipercool : HttpSource() {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 5898568703656160

    override val name = "HipercooL"

    override val baseUrl = "https://hiper.cool"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(SpecificHostRateLimitInterceptor(baseUrl.toHttpUrl(), 1))
        .addInterceptor(SpecificHostRateLimitInterceptor(STATIC_URL.toHttpUrl(), 2))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)
        .add("X-Requested-With", "XMLHttpRequest")

    private val json: Json by injectLazy()

    private fun genericMangaListParse(response: Response): MangasPage {
        val chapters = json.decodeFromString<List<HipercoolChapterDto>>(response.body!!.string())

        if (chapters.isEmpty())
            return MangasPage(emptyList(), false)

        val mangaList = chapters
            .map(::genericMangaFromObject)
            .distinctBy { it.title }

        val hasNextPage = chapters.size == DEFAULT_COUNT

        return MangasPage(mangaList, hasNextPage)
    }

    private fun genericMangaFromObject(chapter: HipercoolChapterDto): SManga = SManga.create().apply {
        title = chapter.book!!.title
        thumbnail_url = chapter.book.slug.toThumbnailUrl(chapter.book.revision)
        url = "/books/" + chapter.book.slug
    }

    // The source does not have popular mangas, so use latest instead.
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = genericMangaListParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val start = (page - 1) * DEFAULT_COUNT
        return GET("$baseUrl/api/books/chapters?start=$start&count=$DEFAULT_COUNT", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = genericMangaListParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        // Create json body.
        val json = buildJsonObject {
            put("start", (page - 1) * DEFAULT_COUNT)
            put("content", DEFAULT_COUNT)
            put("text", query)
            put("type", "text")
        }

        val body = json.toString().toRequestBody(mediaType)

        return POST("$baseUrl/api/books/chapters/search", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage = genericMangaListParse(response)

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsApiRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    private fun mangaDetailsApiRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")

        return GET("$baseUrl/api/books/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val book = json.decodeFromString<HipercoolBookDto>(response.body!!.string())

        val artists = book.tags
            .filter { it.label == "Artista" }
            .flatMap { it.values }
            .joinToString("; ") { it.label }

        val authors = book.tags
            .filter { it.label == "Autor" }
            .flatMap { it.values }
            .joinToString("; ") { it.label }

        val tags = book.tags
            .filter { it.label == "Tags" }
            .flatMap { it.values }
            .joinToString { it.label }

        return SManga.create().apply {
            title = book.title
            thumbnail_url = book.slug.toThumbnailUrl(book.revision)
            description = book.synopsis.orEmpty()
            artist = artists
            author = authors
            genre = tags
        }
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val book = json.decodeFromString<HipercoolBookDto>(response.body!!.string())

        if (book.chapters is JsonPrimitive)
            return emptyList()

        return json.decodeFromString<List<HipercoolChapterDto>>(book.chapters.toString())
            .map { chapterListItemParse(book, it) }
            .reversed()
    }

    private fun chapterListItemParse(book: HipercoolBookDto, chapter: HipercoolChapterDto): SChapter =
        SChapter.create().apply {
            name = "Cap. " + chapter.title
            chapter_number = chapter.title.toFloatOrNull() ?: -1f
            date_upload = chapter.publishedAt.toDate()

            val fullUrl = "$baseUrl/books".toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment(book.slug)
                .addPathSegment(chapter.slug)
                .addQueryParameter("images", chapter.images.toString())
                .addQueryParameter("revision", book.revision.toString())
                .toString()

            setUrlWithoutDomain(fullUrl)
        }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrlOrNull()!!

        val bookSlug = chapterUrl.pathSegments[1]
        val chapterSlug = chapterUrl.pathSegments[2]
        val images = chapterUrl.queryParameter("images")!!.toInt()
        val revision = chapterUrl.queryParameter("revision")!!.toInt()

        val pages = arrayListOf<Page>()

        // Create the pages.
        for (i in 1..images) {
            val imageUrl = "$STATIC_URL/books".toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment(bookSlug)
                .addPathSegment(chapterSlug)
                .addPathSegment("$bookSlug-chapter-$chapterSlug-page-$i.jpg")
                .addQueryParameter("revision", revision.toString())
                .toString()

            pages += Page(i - 1, chapter.url, imageUrl)
        }

        return Observable.just(pages)
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("This method should not be called!")

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl!!)
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun String.toDate(): Long {
        return try {
            DATE_FORMATTER.parse(substringBefore("T"))?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toThumbnailUrl(revision: Int): String =
        "$STATIC_URL/books".toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment(this)
            .addPathSegment("$this-cover.jpg")
            .addQueryParameter("revision", revision.toString())
            .toString()

    companion object {
        private const val STATIC_URL = "https://static.hiper.cool"

        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36"

        private const val DEFAULT_COUNT = 40

        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    }
}
