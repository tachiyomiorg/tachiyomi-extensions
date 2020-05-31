package eu.kanade.tachiyomi.extension.ru.remanga

import BookDto
import BranchesDto
import LibraryDto
import MangaDetDto
import PageDto
import PageWrapperDto
import SeriesWrapperDto
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable

class Remanga : HttpSource() {
    override val name = "Remanga"

    override val baseUrl = "https://remanga.org"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi")
        add("Referer", baseUrl)
    }

    private val count = 30

    private var branches = mutableMapOf<String, List<BranchesDto>>()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/search/catalog/?ordering=rating&count=$count&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/titles/last-chapters/?page=$page&count=$count", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = gson.fromJson<PageWrapperDto<LibraryDto>>(response.body()?.charStream()!!)
        val mangas = page.content.map {
            it.toSManga()
        }
        return MangasPage(mangas, !page.last)
    }

    private fun LibraryDto.toSManga(): SManga =
        SManga.create().apply {
            title = en_name
            url = "/api/titles/$dir/"
            thumbnail_url = "$baseUrl/${img.high}"
        }

    private fun parseDate(date: String?): Long =
        if (date == null)
            Date().time
        else {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(date).time
            } catch (ex: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US).parse(date).time
                } catch (ex: Exception) {
                    Date().time
                }
            }
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/api/search/?query=$query&page=$page")!!.newBuilder()
        return GET(url.toString(), headers)
    }

    private fun parseStatus(status: Int): Int {
        return when (status) {
            0 -> SManga.COMPLETED
            1 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun MangaDetDto.toSManga(): SManga {
        val o = this
        return SManga.create().apply {
            title = en_name
            url = "/api/titles/$dir/"
            thumbnail_url = "$baseUrl/${img.high}"
            this.description = Jsoup.parse(o.description).text()
            genre = (genres + type).joinToString { it.name }
            status = parseStatus(o.status.id)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = gson.fromJson<SeriesWrapperDto<MangaDetDto>>(response.body()?.charStream()!!)
        branches[series.content.en_name] = series.content.branches
        return series.content.toSManga()
    }

    private fun mangaBranches(manga: SManga): List<BranchesDto> {
        val response = client.newCall(GET("$baseUrl/${manga.url}")).execute()
        val series = gson.fromJson<SeriesWrapperDto<MangaDetDto>>(response.body()?.charStream()!!)
        branches[series.content.en_name] = series.content.branches
        return series.content.branches
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val branch = branches.getOrElse(manga.title) { mangaBranches(manga) }
        return if (manga.status != SManga.LICENSED) {
            // Use only first branch for all cases
            client.newCall(chapterListRequest(branch[0].id))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response)
                }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    private fun chapterListRequest(branch: Long): Request {
        return GET("$baseUrl/api/titles/chapters/?branch_id=$branch", headers)
    }

    private fun chapterName(book: BookDto): String {
        val chapterId = if (book.chapter.rem(10) == 0f) book.chapter.toInt() else book.chapter
        var chapterName = "${book.tome} - $chapterId"
        if (book.name.isNotBlank() && chapterName != chapterName) {
            chapterName += "- $chapterName"
        }
        return chapterName
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = gson.fromJson<PageWrapperDto<BookDto>>(response.body()?.charStream()!!)
        return chapters.content.filter { !it.is_paid }.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.chapter
                name = chapterName(chapter)
                url = "/api/titles/chapters/${chapter.id}"
                date_upload = parseDate(chapter.upload_date)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun pageListParse(response: Response): List<Page> {
        val page = gson.fromJson<SeriesWrapperDto<PageDto>>(response.body()?.charStream()!!)
        return page.content.pages.map {
            Page(it.page, "", it.link)
        }
    }

    private val gson by lazy { Gson() }
}
