package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element

class WPComicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ManhuaPlus(),
        ManhuaES(),
        MangaSum(),
        XoxoComics(),
        NhatTruyen(),
        NetTruyen()
    )
}

private class ManhuaPlus : WPComics("Manhua Plus", "https://manhuaplus.com", "en") {
    override val pageListSelector: String = "div.chapter-detail img, ${super.pageListSelector}"
}

private class ManhuaES : WPComics("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US), "+0700") {
    override val popularPath = "category-comics/manga"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.overlay a:has(h2)").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").firstOrNull()?.attr("abs:src")
        }
    }

    override val pageListSelector = "div.chapter-detail img"
}

private class MangaSum : WPComics("MangaSum", "https://mangasum.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/genres?keyword=$query&page=$page", headers)
}

private class XoxoComics : WPComics("XOXO Comics", "https://xoxocomics.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comic-updates?page=$page", headers)
    override fun latestUpdatesSelector() = "li.row"
    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("data-original")
        }
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?keyword=$query&page=$page", headers)
    }
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + "${chapter.url}/all")
}

private class NhatTruyen : WPComics("NhatTruyen", "http://nhattruyen.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/the-loai?keyword=$query&page=$page", headers)
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
}

private class NetTruyen : WPComics("NetTruyen", "http://www.nettruyen.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())

    // search and filters taken from old extension (1.2.5)
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
