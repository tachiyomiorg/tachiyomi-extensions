package eu.kanade.tachiyomi.extension.en.comiclatest

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicLatest : WPComics("ComicLatest", "https://comiclatest.com", "en", SimpleDateFormat("MM/dd/yyyy", Locale.US), null) {
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
