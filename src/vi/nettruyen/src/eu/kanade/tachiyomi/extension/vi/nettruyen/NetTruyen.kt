package eu.kanade.tachiyomi.extension.vi.nettruyen

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NetTruyen : ParsedHttpSource() {

    override val name = "NetTruyen"

    override val baseUrl = "http://nettruyen.com/"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:75.0) Gecko/20100101 Firefox/75.0"
        )
        .add("Referer", baseUrl)

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hot?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.items div.item"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            element.select("figcaption h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("div.image img").attr("data-original")
        }

    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li a"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/tim-truyen-nang-cao")!!.newBuilder()
        val genres = mutableListOf<Int>()
        val genresEx = mutableListOf<Int>()
        if (query.isNotBlank()) {
            return GET("$baseUrl/tim-truyen?keyword=$query&page=$page", headers)
        } else {
            Log.d("Nettruyen", "Inside filter")
            filters.forEach { filter ->
                when (filter) {
                    is NumberOfChapter -> {
                        var numberOfChapter = filter.values[filter.state].split(" ")[1].toInt()
                        if (numberOfChapter == 0) numberOfChapter = 1
                        url.addQueryParameter("minchapter", numberOfChapter.toString())
                    }
                    is Status -> {
                        val statusValue = when (filter.state) {
                            0 -> -1
                            else -> filter.state
                        }
                        url.addQueryParameter("status", statusValue.toString())
                    }
                    is Gender -> {
                        val genderValue = when (filter.state) {
                            0 -> -1
                            else -> filter.state
                        }
                        url.addQueryParameter("gender", genderValue.toString())
                    }
                    is SortBy -> {
                        val sortByKey = when (filter.state) {
                            1 -> 15
                            2 -> 10
                            3 -> 11
                            4 -> 12
                            5 -> 13
                            6 -> 20
                            7 -> 25
                            8 -> 30
                            9 -> 5
                            else -> 0
                        }
                        url.addQueryParameter("sort", sortByKey.toString())
                    }
                    is GenreList -> {
                        filter.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_INCLUDE -> genres.add(genre.id)
                                Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.id)
                            }
                        }
                    }
                }
            }

            url.addQueryParameter("genres", genres.joinToString(","))
            url.addQueryParameter("notgenres", genresEx.joinToString(","))

            Log.d("NetTruyen", "Url: $url")

            return GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("ul.list-info")
        return SManga.create().apply {
            title = document.select("h1.title-detail").text()
            author = infoElement.select("li.author p.col-xs-8").text()
            genre = infoElement.select("li.kind p.col-xs-8 a").joinToString { it.text() }
            status = infoElement.select("li.status p.col-xs-8").text().toStatus()
            thumbnail_url = document.select("div.detail-info div.col-image img").attr("src")
            description = document.select("div.detail-content p").text()
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.equals("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        this.equals("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector(): String = "div.list-chapter li.row:not(.heading)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("div.chapter a").attr("href"))
        name = element.select("div.chapter a").text()
        date_upload = element.select("div.col-xs-4.small").firstOrNull()?.text().toDate()
    }

    private val currentYear by lazy { Calendar.getInstance(Locale.US)[1].toString().takeLast(2) }

    private fun String?.toDate(): Long {
        this ?: return 0L
        return try {
            when {
                this.contains("trước", ignoreCase = true) -> {
                    val trimmedDate = this.substringBefore("trước").split(" ")
                    val num = trimmedDate[0].toIntOrNull() ?: 0
                    val calendar = Calendar.getInstance()
                    when (trimmedDate[1]) {
                        "ngày" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -num) }
                        "giờ" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -num) }
                        "phút" -> calendar.apply { add(Calendar.MINUTE, -num) }
                        "giây" -> calendar.apply { add(Calendar.SECOND, -num) }
                        else -> null
                    }?.timeInMillis ?: 0L
                }
                this.split(" ").size == 2 -> {
                    val date = this.split(" ")[1]
                    SimpleDateFormat("dd/MM/yy", Locale.US).parse("$date/$currentYear")?.time ?: 0L
                }
                else -> SimpleDateFormat("dd/MM/yy", Locale.US).parse("$this")?.time ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-chapter img").mapIndexed { i, img ->
            Log.d("NetTruyen", img.attr("data-original"))
            Page(i, "", img.attr("data-original"))
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    // Filter
    private class Status :
        Filter.Select<String>("Status", arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành"))

    private class NumberOfChapter : Filter.Select<String>(
        "Số lượng chapter",
        arrayOf(
            "> 0 chapter",
            ">= 50 chapter",
            ">= 100 chapter",
            ">= 200 chapter",
            ">= 300 chapter",
            ">= 400 chapter",
            ">= 500 chapter"
        )
    )

    private class Gender :
        Filter.Select<String>("Dành cho", arrayOf("Tất cả", "Con gái", "Con trai"))

    private class SortBy : Filter.Select<String>(
        "Sắp xếp theo",
        arrayOf(
            "Chapter mới",
            "Truyện mới",
            "Xem nhiều nhất",
            "Xem nhiều nhất tháng",
            "Xem nhiều nhất tuần",
            "Xem nhiều nhất hôm nay",
            "Theo dõi nhiều nhất",
            "Bình luận nhiều nhất",
            "Số chapter nhiều nhất",
            "Theo ABC"
        )
    )

    private class Genre(name: String, var id: Int) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    override fun getFilterList() = FilterList(
        NumberOfChapter(),
        Status(),
        Gender(),
        GenreList(getGenreList()),
        SortBy()
    )

    private fun getGenreList(): List<Genre> {
        val genreListName = listOf(
            "Action",
            "Adult",
            "Adventure",
            "Anime",
            "Chuyển Sinh",
            "Comedy",
            "Comic",
            "Cooking",
            "Cổ Đại",
            "Doujinshi",
            "Drama",
            "Đam Mỹ",
            "Ecchi",
            "Fantasy",
            "Gender Bender",
            "Harem",
            "Lịch sử",
            "Horror",
            "Josei",
            "Live action",
            "Manga",
            "Manhua",
            "Manhwa",
            "Martial Arts",
            "Mature",
            "Mecha",
            "Mystery",
            "Ngôn Tình",
            "One shot",
            "Psychological",
            "Romance",
            "School Life",
            "Sci-fi",
            "Seinen",
            "Shoujo",
            "Shoujo Ai",
            "Shounen",
            "Shounen Ai",
            "Slice of Life",
            "Smut",
            "Soft Yaoi",
            "Soft Yuri",
            "Sports",
            "Supernatural",
            "Tạp chí truyện tranh",
            "Thiếu Nhi",
            "Tragedy",
            "Trinh Thám",
            "Truyện Màu",
            "Truyện scan",
            "Việt Nam",
            "Webtoon",
            "Xuyên Không",
            "Yaoi",
            "Yuri",
            "16+",
            "18+"
        )
        val genreList = arrayListOf<Genre>()

        repeat((1..57).count()) { i ->
            genreList.add(Genre(genreListName[i], if (i == 0) 1 else genreList[i - 1].id + 1))
            if (genreList[i].id == 19 || genreList[i].id == 22) genreList[i].id += 1
            if (genreList[i].id == 30) genreList[i].id = 32
        }

        return genreList
    }
}
