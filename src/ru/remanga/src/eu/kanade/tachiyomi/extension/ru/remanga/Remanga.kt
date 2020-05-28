package eu.kanade.tachiyomi.extension.ru.remanga

import BookDto
import LibraryDto
import MangaDetDto
import PageWrapperDto
import SeriesWrapperDto
import android.util.Log
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

class Remanga : HttpSource() {
    override val name = "Remanga"

    override val baseUrl = "https://remanga.org"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/search/catalog/?ordering=rating&count=30&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/titles/last-chapters/?page=$page&count=40", headers)

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
    private fun MangaDetDto.toSManga(): SManga =
        SManga.create().apply {
            title = en_name
            url = "/api/titles/$dir/"
            thumbnail_url = "$baseUrl/${img.high}"
        }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = gson.fromJson<SeriesWrapperDto<MangaDetDto>>(response.body()?.charStream()!!)
        Log.i("Response", series.toString())
        return series.content.toSManga()
    }
    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl/api/titles/chapters/?branch_id=${manga.branch}", headers)
    override fun chapterListParse(response: Response): List<SChapter> {
        val page = gson.fromJson<PageWrapperDto<BookDto>>(response.body()?.charStream()!!)

        return page.content.map { book ->
            SChapter.create().apply {
                chapter_number = book.chapter
                name = book.name
                url = "$baseUrl/api/titles/chapters/${book.id}"
                date_upload = parseDate(book.upload_date)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun pageListParse(response: Response): List<Page> {
        TODO("Not yet implemented")
    }

    private val gson by lazy { Gson() }
}
