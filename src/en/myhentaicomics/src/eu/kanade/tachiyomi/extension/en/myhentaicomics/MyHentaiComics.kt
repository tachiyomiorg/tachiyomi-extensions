package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MyHentaiComics : ParsedHttpSource() {

    override val name = "MyHentaiComics"

    override val baseUrl = "https://myhentaicomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/index.php/tag/2402?page=$page", headers)
    }

    override fun popularMangaSelector() = "li.g-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h2").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.ui-state-default span.ui-icon-seek-next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/index.php/?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/index.php/search?q=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("p").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val tags = document.select("div.g-description a").partition { tag ->
            tag.text().startsWith("Artist: ")
        }
        return SManga.create().apply {
            artist = tags.first.joinToString { it.text().substringAfter(" ") }
            author = artist
            genre = tags.second.joinToString { it.text() }
            thumbnail_url = document.select("img.g-thumbnail").first().attr("abs:src").replace("/thumbs/", "/resizes/")
        }
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                }
            )
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.g-thumbnail").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src").replace("/thumbs/", "/resizes/"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
