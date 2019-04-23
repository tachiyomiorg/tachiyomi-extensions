package eu.kanade.tachiyomi.extension.vi.nettruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class NetTruyen : ParsedHttpSource() {

    override val name = "NetTruyen"

    override val baseUrl = "http://www.nettruyen.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

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
//            manga.thumbnail_url = it.select("img").first()?.attr("src")
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
                                        "tim-truyen/${getGenreList()[filter.state]}?"))!!
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
        chapter.date_upload = element.select(".col-xs-4.text-center").last()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dates: Calendar = Calendar.getInstance()
        if (date.contains("/")){
            return if (date.contains(":")){
                // Format eg 17:02 20/04
                val dateDM = date.split(" ")[1].split("/")
                dates.set(dates.get(Calendar.YEAR), dateDM[1].toInt(), dateDM[0].toInt())
                dates.timeInMillis
            }else{
                // Format eg 18/11/17
                val dateDMY = date.split("/")
                dates.set(2000+ dateDMY[2].toInt(), dateDMY[1].toInt(), dateDMY[0].toInt())
                dates.timeInMillis
            }
        }else{
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

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".page-chapter img").forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    private fun getStatusList() = arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành", "Tạm ngừng")

    private class Status(status: Array<String>) : Filter.Select<String>("Status", status)
    private class Genre(genreList: Array<String>) : Filter.Select<String>("Thể loại", genreList)

    override fun getFilterList() = FilterList(
            Status(getStatusList()),
            Genre(getGenreList())
    )

    private fun getGenreList() = arrayOf(
            "Tất cả",
            "action", "adult",
            "adventure", "anime",
            "chuyen-sinh", "comedy",
            "comic", "cooking",
            "co-dai", "doujinshi",
            "drama", "dam-my",
            "ecchi", "fantasy",
            "gender-bender", "harem",
            "historical", "horror",
            "josei", "live-action",
            "manga", "manhua",
            "manhwa", "martial-arts",
            "mature", "mecha",
            "mystery", "ngon-tinh",
            "one-shot", "psychological",
            "romance", "school-life",
            "sci-fi", "seinen",
            "shoujo", "shoujo-ai",
            "shounen", "shounen-ai",
            "slice-of-life", "smut",
            "soft-yaoi", "soft-yuri",
            "sports", "supernatural",
            "thieu-nhi", "tragedy",
            "trinh-tham", "truyen-scan",
            "truyen-mau", "webtoon",
            "xuyen-khong"
    )
}
