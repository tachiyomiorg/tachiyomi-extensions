package eu.kanade.tachiyomi.extension.en.dragonball_multiverse

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class DbMultiverse : ParsedHttpSource() {

    override val name = "Dragon Ball Multiverse"
    override val baseUrl = "https://www.dragonball-multiverse.com"
    override val supportsLatest = false
    override val lang = "en"

    private fun chapterFromElement(element: Element, name: String): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("abs:href"))
        chapter.name = name + element.text().let { num ->
            if (num.contains("-")) {
                "Pages $num"
            } else {
                "Page $num"
            }
        }
        return chapter
    }

    override fun chapterListSelector(): String = "div.cadrelect.chapters a[href*=page-]"

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()

        document.select("div[ch]").forEach { container ->
            container.select(chapterListSelector()).mapIndexed { i, chapter ->
                // Each page is its own chapter, add chapter name when a first page is mapped
                val name = if (i == 0) container.select("h4").text() + " - " else ""
                chapters.add(chapterFromElement(chapter, name))
            }
        }

        return chapters.reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#h_read img").mapIndexed { index, element ->
            Page(index, "", element.attr("abs:src"))
        }
    }

    override fun mangaDetailsParse(document: Document): SManga = createManga(document)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(createManga(null)), hasNextPage = false))
    }

    private fun createManga(document: Document?) = SManga.create().apply {
        title = name
        status = SManga.ONGOING
        url = "/en/chapters.html"
        description = "Dragon Ball Multiverse (DBM) is a free online comic, made by a whole team of fans. It's our personal sequel to DBZ."
        thumbnail_url = document?.select("div[ch=\"1\"] img")?.attr("abs:src")
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.empty()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
}
