package eu.kanade.tachiyomi.extension.ru.remanga

import BookDto
import BranchesDto
import GenresDto
import LibraryDto
import MangaDetDto
import PageDto
import PageWrapperDto
import SeriesWrapperDto
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
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
        var url = HttpUrl.parse("$baseUrl/api/search/catalog/?page=$page")!!.newBuilder()
        if (query.isNotEmpty()) {
            url = HttpUrl.parse("$baseUrl/api/search/?page=$page")!!.newBuilder()
            url.addQueryParameter("query", query)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryList -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (category.isIncluded()) "types" else "exclude_types", category.id)
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state != false) {
                        url.addQueryParameter("status", status.id)
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (genre.isIncluded()) "genres" else "exclude_genres", genre.id)
                    }
                }
                is OrderBy -> {
                    var ord = arrayOf("id", "chapter_date", "rating", "votes", "views", "random")[filter.state!!.index]
                    if (!filter.state!!.ascending) {
                        ord = "-" + ord
                    }
                    url.addQueryParameter("ordering", ord)
                }
            }
        }
        return GET(url.toString(), headers)
    }

    private fun parseStatus(status: Int): Int {
        return when (status) {
            0 -> SManga.COMPLETED
            1 -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun parseType(type: GenresDto): GenresDto {
        return when (type.name) {
            "Западный комикс" -> GenresDto(type.id, "Комикс")
            else -> type
        }
    }

    private fun MangaDetDto.toSManga(): SManga {
        val o = this
        return SManga.create().apply {
            title = en_name
            url = "/api/titles/$dir/"
            thumbnail_url = "$baseUrl/${img.high}"
            this.description = Jsoup.parse(o.description).text()
            genre = (genres + parseType(type)).joinToString { it.name }
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
        val chapterId = if (book.chapter % 1 == 0f) book.chapter.toInt() else book.chapter
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
    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class CategoryList(categories: List<SearchFilter>) : Filter.Group<SearchFilter>("Категории", categories)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)

    override fun getFilterList() = FilterList(
        CategoryList(getCategoryList()),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
        OrderBy()
    )

    private class OrderBy : Filter.Sort("Сортировка",
        arrayOf("Новизне", "Последним обновлениям", "Популярности", "Лайкам", "Просмотрам", "Мне повезет"),
        Selection(2, false))

    /*
    * Use console
    * Object.entries(__FILTER_ITEMS__.types).map(([k, v]) => `SearchFilter("${v.label}", "${v.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getCategoryList() = listOf(
        SearchFilter("Манга", "0"),
        SearchFilter("Манхва", "1"),
        SearchFilter("Маньхуа", "2"),
        SearchFilter("Западный комикс", "3"),
        SearchFilter("Русскомикс", "4"),
        SearchFilter("Индонезийский комикс", "5"),
        SearchFilter("Новелла", "6"),
        SearchFilter("Другое", "7")
    )

    /*
    * Use console
    * Object.entries(__FILTER_ITEMS__.status).map(([k, v]) => `SearchFilter("${v.label}", "${v.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getStatusList() = listOf(
        CheckFilter("Закончен", "0"),
        CheckFilter("Продолжается", "1"),
        CheckFilter("Заморожен", "2")
    )

    /*
    * Use console
    * __FILTER_ITEMS__.genres.map(it => `SearchFilter("${it.name}", "${it.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getGenreList() = listOf(
        SearchFilter("арт", "1"),
        SearchFilter("бдсм", "44"),
        SearchFilter("боевик", "2"),
        SearchFilter("боевые искусства", "3"),
        SearchFilter("вампиры", "4"),
        SearchFilter("гарем", "5"),
        SearchFilter("гендерная интрига", "6"),
        SearchFilter("героическое фэнтези", "7"),
        SearchFilter("детектив", "8"),
        SearchFilter("дзёсэй", "9"),
        SearchFilter("додзинси", "10"),
        SearchFilter("драма", "11"),
        SearchFilter("игра", "12"),
        SearchFilter("история", "13"),
        SearchFilter("киберпанк", "14"),
        SearchFilter("кодомо", "15"),
        SearchFilter("комедия", "16"),
        SearchFilter("махо-сёдзё", "17"),
        SearchFilter("меха", "18"),
        SearchFilter("мистика", "19"),
        SearchFilter("научная фантастика", "20"),
        SearchFilter("повседневность", "21"),
        SearchFilter("постапокалиптика", "22"),
        SearchFilter("приключения", "23"),
        SearchFilter("психология", "24"),
        SearchFilter("романтика", "25"),
        SearchFilter("сверхъестественное", "27"),
        SearchFilter("сёдзё", "28"),
        SearchFilter("сёдзё-ай", "29"),
        SearchFilter("сёнэн", "30"),
        SearchFilter("сёнэн-ай", "31"),
        SearchFilter("спорт", "32"),
        SearchFilter("сэйнэн", "33"),
        SearchFilter("трагедия", "34"),
        SearchFilter("триллер", "35"),
        SearchFilter("ужасы", "36"),
        SearchFilter("фантастика", "37"),
        SearchFilter("фэнтези", "38"),
        SearchFilter("школа", "39"),
        SearchFilter("эротика", "42"),
        SearchFilter("этти", "40"),
        SearchFilter("юри", "41"),
        SearchFilter("яой", "43")
    )

    private val gson by lazy { Gson() }
}
