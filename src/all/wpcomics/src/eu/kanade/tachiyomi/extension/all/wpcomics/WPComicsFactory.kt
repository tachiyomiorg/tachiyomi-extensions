package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WPComicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ManhuaES(),
        MangaSum(),
        MangaSumRAW(),
        XoxoComics(),
        NhatTruyen(),
        NetTruyen(),
        TruyenChon(),
        ComicLatest()
    )
}

private class ManhuaES : WPComics("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US), "+0700") {
    override val popularPath = "category-comics/manga"
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$popularPath" + if (page > 1) "/page/$page" else "", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl + if (page > 1) "/page/$page" else "", headers)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query&post_type=comics")
    }
    override fun popularMangaNextPageSelector() = ".pagination li:last-child:not(.active)"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.overlay a:has(h2)").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").firstOrNull()?.attr("abs:src")
        }
    }
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select(".tags-genre a").joinToString { it.text() }
                thumbnail_url = imageOrNull(info.select("div.col-image img").first())

                val h3 = info.select(".detail-content h3").text()
                val strong = info.select(".detail-content strong").text()
                val showMoreFake = info.select(".detail-content .content-readmore").text()
                val showMore = info.select(".detail-content .morelink").text()
                val rawDesc = info.select("div.detail-content").text()
                
                if (showMoreFake == null || showMoreFake == "") {
                    description = rawDesc.substringAfter(h3).substringAfter(strong).substringBefore(showMore)
                } else {
                    description = rawDesc.substringAfter(h3).substringAfter(strong).substringBefore(showMoreFake)
                }
            }
        }
    }
    override val pageListSelector = "div.chapter-detail ul img, div.chapter-detail div:not(.container) > img, div.chapter-detail p > img"
}

private class MangaSumRAW : WPComics("MangaSum RAW", "https://mangasum.com", "ja", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/raw" + if (page > 1) "?page=$page" else "", headers)
    }
    override fun popularMangaSelector() = "div.items div.item"
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/genres?keyword=$query&page=$page", headers)
    override fun searchMangaSelector() = "div.items div.item div.image a[title*=' - Raw']"
}

private class MangaSum : WPComics("MangaSum", "https://mangasum.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/genres?keyword=$query&page=$page", headers)
    override fun searchMangaSelector() = "div.items div.item div.image a:not([title*=' - Raw'])"
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // recursively add chapters from paginated chapter list
        fun parseChapters(document: Document) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select("ul.pagination a[rel=next]").firstOrNull()?.let { a ->
                parseChapters(client.newCall(GET(a.attr("abs:href"), headers)).execute().asJsoup())
            }
        }

        parseChapters(response.asJsoup())
        return chapters
    }
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + "${chapter.url}/all")
}

private class NhatTruyen : WPComics("NhatTruyen", "http://nhattruyen.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/the-loai?keyword=$query&page=$page", headers)
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
}

private class NetTruyen : WPComics("NetTruyen", "http://www.nettruyen.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(getStatusList()),
            GenreFilter(getGenreList())
        )
    }
}

private class TruyenChon : WPComics("TruyenChon", "http://truyenchon.com", "vi", SimpleDateFormat("dd/MM/yy", Locale.getDefault()), null) {
    override val searchPath = "the-loai"
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().add("Referer", baseUrl).build())
    override fun getFilterList(): FilterList {
        return FilterList(
            StatusFilter(getStatusList()),
            GenreFilter(getGenreList())
        )
    }
}

private class ComicLatest : WPComics("ComicLatest", "https://comiclatest.com", "en", SimpleDateFormat("MM/dd/yyyy", Locale.US), null) {
    // Hot only has one page
    override val popularPath = "popular-comics"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("h3 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.select("img").attr("data-original")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    val author = filter.state.trim().replace(" ", "-").toLowerCase()
                    return GET("$baseUrl/author/$author?page=$page", headers)
                }
            }
        }

        return GET("$baseUrl/search?keyword=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = "div.item div.box_img > a[title]"

    // For whatever reason, errors with author search if this isn't overridden
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.attr("title")
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        fun parseChapters(document: Document) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select("ul.pagination a[rel=next]").firstOrNull()?.let { a ->
                parseChapters(client.newCall(GET(a.attr("abs:href"), headers)).execute().asJsoup())
            }
        }

        parseChapters(response.asJsoup())
        return chapters
    }

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl${chapter.url}/all", headers)

    private class AuthorFilter : Filter.Text("Author")

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Cannot be used with search"),
        Filter.Separator(),
        AuthorFilter()
    )
}
