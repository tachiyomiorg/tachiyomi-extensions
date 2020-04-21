package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WPComicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ManhuaPlus(),
        ManhuaES(),
        MangaSum(),
        XoxoComics()
    )
}

private class ManhuaPlus : WPComics("Manhua Plus", "https://manhuaplus.com", "en")

private class ManhuaES : WPComics("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US), "+0700") {
    override val popularPath = "category-comics/manga"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.overlay a").let {
                title = it.attr("title")
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("div.image img").attr("abs:src")
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a.head").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override val pageListSelector = "div.chapter-detail img"
}

private class MangaSum : WPComics("MangaSum", "https://mangasum.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/genres?keyword=$query&page=$page", headers)
    /**
     * TODO - chapter dates come in 3 flavors: relative dates less than a month, time + month/day (current year is implied),
     * and MM/dd/yy; see about getting all 3 working (currently at 2/3)
     */
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
