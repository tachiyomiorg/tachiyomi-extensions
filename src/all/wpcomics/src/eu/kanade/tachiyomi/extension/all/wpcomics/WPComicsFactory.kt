package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.Request
import org.jsoup.nodes.Element

class WPComicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ManhuaPlus(),
        ManhuaES(),
        MangaSum(),
        XoxoComics(),
        NhatTruyen()
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
