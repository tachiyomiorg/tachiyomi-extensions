package eu.kanade.tachiyomi.extension.vi.nettruyen

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class NetTruyen : ParsedHttpSource() {

    override val name = "NetTruyen"

    override val baseUrl = "http://www.nettruyen.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().addInterceptor {
        //Intercept any image requests and add a referer to them
        //Enables bandwidth stealing feature
        val request =
                if (it.request().url().host().contains("cloud"))
                    it.request().newBuilder().addHeader("Referer", baseUrl).build()
                else it.request()
        it.proceed(request)
    }.build()!!

    override fun popularMangaSelector() = "#ctl00_divCenter div.items div.item"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/tim-truyen?status=-1&sort=11&page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/tim-truyen?page=$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title").replace("Truyện tranh", "").trim()
            manga.thumbnail_url = it.select("img").first()?.attr("data-original")
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "ul a.next-page"

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = HttpUrl.parse("$baseUrl/tim-truyen?")!!.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Genre -> {
                    url = if (filter.state == 0) url else
                        HttpUrl.parse(url.toString()
                                .replace("tim-truyen?",
                                        "tim-truyen/${getGenreList().map { it.first }[filter.state]}?"))!!
                                .newBuilder()
                }
                is Status -> {
                    url.addQueryParameter("status", if (filter.state == 0) "hajau" else filter.state.toString())
                    url.addQueryParameter("sort", "0")
                }
            }
        }
        url.addQueryParameter("keyword", query)
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("#item-detail").first()

        val manga = SManga.create()
        manga.author = infoElement.select(".author a").first()?.text()
        manga.genre = infoElement.select(".kind a").joinToString { it.text() }
        manga.description = infoElement.select("#item-detail > div.detail-content > p").text()
        manga.status = infoElement.select(".status > p.col-xs-8").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = infoElement.select("img").attr("src")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#nt_listchapter li:not(.heading)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select(".col-xs-4.text-center").last()?.text()?.let { parseChapterDate(it) }
                ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dates: Calendar = Calendar.getInstance()
        if (date.contains("/")) {
            return if (date.contains(":")) {
                // Format eg 17:02 20/04
                val dateDM = date.split(" ")[1].split("/")
                dates.set(dates.get(Calendar.YEAR), dateDM[1].toInt() - 1, dateDM[0].toInt())
                dates.timeInMillis
            } else {
                // Format eg 18/11/17
                val dateDMY = date.split("/")
                dates.set(2000 + dateDMY[2].toInt(), dateDMY[1].toInt() - 1, dateDMY[0].toInt())
                dates.timeInMillis
            }
        } else {
            // Format eg 1 ngày trước
            val dateWords: List<String> = date.split(" ")
            if (dateWords.size == 3) {
                val timeAgo = Integer.parseInt(dateWords[0])
                when {
                    dateWords[1].contains("phút") -> dates.add(Calendar.MINUTE, -timeAgo)
                    dateWords[1].contains("giờ") -> dates.add(Calendar.HOUR_OF_DAY, -timeAgo)
                    dateWords[1].contains("ngày") -> dates.add(Calendar.DAY_OF_YEAR, -timeAgo)
                    dateWords[1].contains("tuần") -> dates.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                    dateWords[1].contains("tháng") -> dates.add(Calendar.MONTH, -timeAgo)
                    dateWords[1].contains("năm") -> dates.add(Calendar.YEAR, -timeAgo)
                }
                return dates.timeInMillis
            }
        }
        return 0L
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".page-chapter img").forEach {
            val imageUrl = it.attr("data-original")
            pages.add(Page(pages.size, "", if(imageUrl.startsWith("//")) "http:$imageUrl" else imageUrl ))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    private fun getStatusList() = arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành", "Tạm ngừng")

    private class Status(status: Array<String>) : Filter.Select<String>("Status", status)
    private class Genre(genreList: Array<String>) : Filter.Select<String>("Thể loại", genreList)

    override fun getFilterList() = FilterList(
            Status(getStatusList()),
            Genre(getGenreList().map { it.second }.toTypedArray())
    )

    private fun getGenreList() = arrayOf(
            "tim-truyen" to "Tất cả",
            "action" to "Action",
            "adult" to "Adult",
            "adventure" to "Adventure",
            "anime" to "Anime",
            "chuyen-sinh" to "Chuyển Sinh",
            "comedy" to "Comedy",
            "comic" to "Comic",
            "cooking" to "Cooking",
            "co-dai" to "Cổ Đại",
            "doujinshi" to "Doujinshi",
            "drama" to "Drama",
            "dam-my" to "Đam Mỹ",
            "ecchi" to "Ecchi",
            "fantasy" to "Fantasy",
            "gender-bender" to "Gender Bender",
            "harem" to "Harem",
            "historical" to "Historical",
            "horror" to "Horror",
            "josei" to "Josei",
            "live-action" to "Live action",
            "manga" to "Manga",
            "manhua" to "Manhua",
            "manhwa" to "Manhwa",
            "martial-arts" to "Martial Arts",
            "mature" to "Mature",
            "mecha" to "Mecha",
            "mystery" to "Mystery",
            "ngon-tinh" to "Ngôn Tình",
            "one-shot" to "One shot",
            "psychological" to "Psychological",
            "romance" to "Romance",
            "school-life" to "School Life",
            "sci-fi" to "Sci-fi",
            "seinen" to "Seinen",
            "shoujo" to "Shoujo",
            "shoujo-ai" to "Shoujo Ai",
            "shounen" to "Shounen",
            "shounen-ai" to "Shounen Ai",
            "slice-of-life" to "Slice of Life",
            "smut" to "Smut",
            "soft-yaoi" to "Soft Yaoi",
            "soft-yuri" to "Soft Yuri",
            "sports" to "Sports",
            "supernatural" to "Supernatural",
            "thieu-nhi" to "Thiếu Nhi",
            "tragedy" to "Tragedy",
            "trinh-tham" to "Trinh Thám",
            "truyen-scan" to "Truyện scan",
            "truyen-mau" to "Truyện Màu",
            "webtoon" to "Webtoon",
            "xuyen-khong" to "Xuyên Không"
    )
}