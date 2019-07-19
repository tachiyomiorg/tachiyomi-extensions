package eu.kanade.tachiyomi.extension.en.dragonball_multiverse

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class DbMultiverse : ParsedHttpSource() {

    override val name = "Dragon Ball Multiverse"
    override val baseUrl = "http://www.dragonball-multiverse.com"
    override val supportsLatest = false
    override val lang = "en"

    override fun chapterFromElement(element: Element): SChapter {
        val chapterUrl = element.attr("href")

        val chapter = SChapter.create()
        chapter.url = "/en/$chapterUrl"
        chapter.name = element.text().let { name ->
            if (name.contains("-")) {
                "Pages $name"
            } else {
                "Page $name"
            }
        }
        return chapter
    }

    override fun chapterListSelector(): String = "div.cadrelect.chapters a[href*=page-]"

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[src*=/final/]").mapIndexed { index, element ->
            Page(index, "", baseUrl + element.attr("src"))
        }
    }

    override fun mangaDetailsParse(document: Document): SManga = createManga()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(createManga()), hasNextPage = false))
    }

    private fun createManga() = SManga.create().apply {
        title = name
        status = SManga.ONGOING
        url = "/en/chapters.html"
        description = "Dragon Ball Multiverse (DBM) is a free online comic, made by a whole team of fans. It's our personal sequel to DBZ."
        thumbnail_url = "$baseUrl/en/pages/final/0000.jpg"
    }

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