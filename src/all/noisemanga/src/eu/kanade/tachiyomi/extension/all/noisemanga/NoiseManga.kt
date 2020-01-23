package eu.kanade.tachiyomi.extension.all.noisemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.lang.Exception

abstract class NoiseManga(override val lang: String) : ParsedHttpSource() {

    override val name = "NOISE"

    override val baseUrl = "https://noisemanga.com"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "ul#menu-home li a[title=\"Séries\"] + ul li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.text()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Since there are only three series, it's worth to do a client-side search.
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map {
                val mangas = it.mangas.filter { m -> m.title.contains(query, true) }
                MangasPage(mangas, it.hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val mainContent = document.select("div.main-content-page").first()
        val entryContent = mainContent.select("div.entry-content").first()
        val descriptionSelector = if (lang == "en") "hr + h4, hr + div h4" else "h1 + h4"

        return SManga.create().apply {
            title = mainContent.select("header h1.single-title").first()!!.text()
            status = SManga.UNKNOWN
            description = entryContent.select(descriptionSelector).first()!!.text()
            thumbnail_url = entryContent.select("h1 img.alignleft").first()!!.attr("src")
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListSelector(): String {
        val columnSelector = if (lang == "pt") 1 else 2

        return "div.entry-content div table tr td:nth-child($columnSelector) a"
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        scanlator = "NOISE Manga"
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select("div.single-content div.single-entry-summary img.aligncenter")

        return pages
            .map {
                it.attr("srcset")
                    .substringAfterLast(", ")
                    .substringBeforeLast(" ")
            }
            .mapIndexed { i, imgUrl -> Page(i, "", imgUrl)}
    }

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesRequest(page: Int) = throw Exception("This method should not be called!")

    override fun latestUpdatesSelector() = throw Exception("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) = throw Exception("This method should not be called!")

    override fun latestUpdatesNextPageSelector() = throw Exception("This method should not be called!")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
    }
}
