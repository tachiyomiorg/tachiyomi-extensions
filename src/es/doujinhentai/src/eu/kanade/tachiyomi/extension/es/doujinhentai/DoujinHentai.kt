package eu.kanade.tachiyomi.extension.es.doujinhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class DoujinHentai : ParsedHttpSource() {

    override val baseUrl = "https://doujinhentai.net"

    override val lang = "es"

    override val name = "DoujinHentai"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/lista-manga-hentai?orderby=views&page=$page", headers)

    override fun popularMangaSelector() = "div.page-content-listing > div.page-listing-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("div.page-item-detail").let {
            it.select("div.item-summary > div.post-title > h5 > a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = it.select("div.item-thumb > a > img").attr("data-src")
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    override fun latestUpdatesRequest(page: Int)= GET("$baseUrl/lista-manga-hentai?orderby=last&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div.tab-summary").let {
            thumbnail_url = it.select("div.summary_image > img").attr("data-src")

            it.select("div.summary_content_wrap > div.summary_content").let {
                author = document.select("div.author-content > a").text()
                artist = document.select("div.artist-content > a").text()
                genre = document.select("div.genres-content > a").joinToString(", ") {
                    it.text()
                }
                it.select("div.post-status").let {
                    status = parseStatus(it.select("span.label").text().orEmpty())
                }
            }
        }

        description = document.select("div.description-summary").text().orEmpty()
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Complete") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.main.version-chap > li.wp-manga-chapter:not(:last-child)" // removing empty li

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.select("a").text()
        setUrlWithoutDomain(element.select("a").attr("href"))
        scanlator = element.select("span.chapter-release-date > a").text()
        date_upload = parseChapterDate(element.select("span.chapter-release-date > i").text())
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            return try {
                SimpleDateFormat("d MMM. yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) {
                0L
            }
        }

        return 0L
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div#all > img.img-responsive")?.forEach {
            add(Page(size, "", it.attr("data-src")))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search?query=$query", headers)

    override fun searchMangaSelector() = ".c-tabs-item__content .c-tabs-item__content"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("div.tab-thumb.c-image-hover > a > img").attr("data-src")
        setUrlWithoutDomain(element.select("div.tab-thumb.c-image-hover > a").attr("href"))
        title = element.select("div.tab-thumb.c-image-hover > a").attr("title")
    }

    override fun searchMangaNextPageSelector() = "#not_actually_used"

}
