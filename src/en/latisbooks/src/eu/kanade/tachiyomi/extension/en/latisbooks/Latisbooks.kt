package eu.kanade.tachiyomi.extension.en.latisbooks

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.Calendar

class Latisbooks : HttpSource() {

    override val name = "Latis Books"

    override val baseUrl = "https://www.latisbooks.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private fun createManga(response: Response): SManga {
        return SManga.create().apply {
            initialized = true
            title = "Bodysuit 23"
            url = "/archive/"
            thumbnail_url = response.asJsoup().select("img.thumb-image").firstOrNull()?.attr("abs:data-src")
        }
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                MangasPage(listOf(createManga(response)), false)
            }
    }

    override fun popularMangaRequest(page: Int): Request {
        return (GET("$baseUrl/archive/", headers))
    }

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                createManga(response)
            }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val cal: Calendar = Calendar.getInstance()

        return response.asJsoup().select("ul.archive-item-list li a").map {
            val date: List<String> = it.attr("abs:href").split("/")
            cal.set(date[4].toInt(), date[5].toInt() - 1, date[6].toInt())

            SChapter.create().apply {
                name = it.text()
                url = it.attr("abs:href")
                date_upload = cal.timeInMillis
            }
        }
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, chapter.url)))
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String {
        return response.asJsoup().select("div.content-wrapper img.thumb-image").attr("abs:data-src")
    }

    override fun getFilterList() = FilterList()
}
