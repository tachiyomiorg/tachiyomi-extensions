package eu.kanade.tachiyomi.extension.pt.bruttal

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class Bruttal : HttpSource() {

    override val name = "Bruttal"

    override val baseUrl = "https://originals.omelete.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/bruttal/")
        .add("User-Agent", USER_AGENT)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .build()

        return GET("$baseUrl/bruttal/data/home.json", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val homeDto = json.decodeFromString<BruttalHomeDto>(response.body!!.string())

        val titles = homeDto.list.map(::popularMangaFromObject)

        return MangasPage(titles, false)
    }

    private fun popularMangaFromObject(comicbook: BruttalComicBookDto): SManga = SManga.create().apply {
        title = comicbook.title
        thumbnail_url = "$baseUrl/bruttal/" + comicbook.imageMobile.removePrefix("./")
        url = "/bruttal" + comicbook.url
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
        val comicBooks = json.decodeFromString<List<BruttalComicBookDto>>(response.body!!.string())

        val comicBookUrl = response.request.header("Referer")!!
            .substringAfter("/bruttal")
        val currentComicBook = comicBooks.first { it.url == comicBookUrl }

        return SManga.create().apply {
            title = currentComicBook.title
            thumbnail_url = "$baseUrl/bruttal/" + currentComicBook.imageMobile.removePrefix("./")
            description = currentComicBook.synopsis +
                (if (currentComicBook.soonText.isEmpty()) "" else "\n\n${currentComicBook.soonText}")
            artist = currentComicBook.illustrator
            author = currentComicBook.author
            genre = currentComicBook.keywords.replace("; ", ", ")
            status = SManga.ONGOING
        }
    }

    // Chapters are available in the same url of the manga details.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsApiRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val comicBooks = json.decodeFromString<List<BruttalComicBookDto>>(response.body!!.string())

        val comicBookUrl = response.request.header("Referer")!!
            .substringAfter("/bruttal")
        val currentComicBook = comicBooks.first { it.url == comicBookUrl }

        return currentComicBook.seasons
            .flatMap { it.chapters }
            .map(::chapterFromObject)
            .reversed()
    }

    private fun chapterFromObject(chapter: BruttalChapterDto): SChapter = SChapter.create().apply {
        name = chapter.title
        chapter_number = chapter.shareTitle
            .removePrefix("Capítulo ")
            .toFloatOrNull() ?: -1f
        url = "/bruttal" + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val newHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .set("Referer", baseUrl + chapter.url)
            .build()

        return GET("$baseUrl/bruttal/data/comicbooks.json", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val comicBooks = json.decodeFromString<List<BruttalComicBookDto>>(response.body!!.string())

        val chapterUrl = response.request.header("Referer")!!
        val comicBookSlug = chapterUrl
            .substringAfter("bruttal/")
            .substringBefore("/")
        val seasonNumber = chapterUrl
            .substringAfter("temporada-")
            .substringBefore("/")
        val chapterNumber = chapterUrl.substringAfter("capitulo-")

        val currentComicBook = comicBooks.first { it.url == "/$comicBookSlug" }
        val currentSeason = currentComicBook.seasons.first {
            it.alias.substringAfter("-") == seasonNumber
        }
        val currentChapter = currentSeason.chapters.first {
            it.alias.substringAfter("-") == chapterNumber
        }

        return currentChapter.images
            .mapIndexed { i, bruttalImage ->
                val imageUrl = "$baseUrl/bruttal/" + bruttalImage.image.removePrefix("./")
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

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36"
    }
}
