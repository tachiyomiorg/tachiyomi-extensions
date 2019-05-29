package eu.kanade.tachiyomi.extension.en.keenspot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Keenspot : ParsedHttpSource() {

    val invalidSelector = "head > body"

    override fun latestUpdatesSelector(): String {
        return invalidSelector
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override val name = "Keenspot"

    override val baseUrl = "http://www.keenspot.com/"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient get() = network.client

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector() = "table:nth-child(2) td:nth-child(3) > table:nth-child(3) table a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.let {
            manga.url = java.net.URL(java.net.URL(it.attr("href")), "/archive").toString()
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = invalidSelector

    override fun latestUpdatesNextPageSelector() = invalidSelector

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaSelector() = invalidSelector

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        return manga
    }

    override fun searchMangaNextPageSelector() = invalidSelector

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("head").first()

        val manga = SManga.create()
        manga.description = detailElement.select("meta[name=description]").first()?.attr("content")
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }

    override fun chapterListSelector() = ".chapter a:has(img)"

    override fun chapterFromElement(element: Element): SChapter {

        val chapter = SChapter.create()
        chapter.url = java.net.URL(java.net.URL(element.baseUri()), element.attr("href")).toString()
        chapter.name = element.text()
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        pages.add(Page(1, document.baseUri()))
        return pages
    }

    override fun imageUrlParse(document: Document) = document.select("img[alt=Comic Page]").first().attr("src")

    override fun getFilterList() = FilterList()

}