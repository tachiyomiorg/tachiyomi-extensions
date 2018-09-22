package eu.kanade.tachiyomi.extension.ru.mangahub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

open class Mangahub : ParsedHttpSource() {

    override val name = "Mangahub"

    override val baseUrl = "http://mangahub.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/explore?search%5Bsort%5D=rating&search%5BdateStart%5D%5Bleft_number%5D=1972&search%5BdateStart%5D%5Bright_number%5D=2018&page=${page + 1}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/explore?search%5Bsort%5D=update&search%5BdateStart%5D%5Bleft_number%5D=1972&search%5BdateStart%5D%5Bright_number%5D=2018&page=${page + 1}", headers)

    override fun popularMangaSelector() = "div.list-element"

    override fun latestUpdatesSelector() = "div.list-element"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.list-element__image-back").attr("style").removeSuffix("')").removePrefix("background-image:url('")
        manga.title = element.select("div.list-element__name").text()
        manga.setUrlWithoutDomain(element.select("div.list-element__name > a").attr("href"))
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".next"

    override fun latestUpdatesNextPageSelector() = ".next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/manga?query=$query&sort=score&page=${page + 1}")
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = ".next"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = if(document.select("[itemprop=\"author\"]") != null) document.select("[itemprop=\"author\"]").text()
        manga.genre = document.select("div.b-dtl-desc__labels")[0].text().replace(" ", ", ")
        manga.description = if (document.select("div.b-dtl-desc__desc-info > p").last() != null) document.select("div.b-dtl-desc__desc-info > p").last().text() else null
        manga.status = parseStatus(document)
        manga.thumbnail_url = document.select("div.manga-section-image__img > [itemprop=\"image\"]").attr("src")
        return manga
    }

    private fun parseStatus(element: Document): Int = when {
        element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-completed").size != 0 -> SManga.COMPLETED
        element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-translated").size != 0
                && element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-updated").size == 0 -> SManga.COMPLETED
        element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-updated").size != 0 -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.b-catalog-list__elem"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("div.b-ovf-table__elem > a").first()
        val chapter = SChapter.create()
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.b-catalog-el__date-val").text()?.let {
            SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(it).time
        } ?: 0
        val url = element.select("div.b-ovf-table__elem a").first().attr("href")
        chapter.setUrlWithoutDomain(url)
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Глава\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pictures = document.select("div.b-reader.b-reader__full").attr("data-js-scans").replace("&quot;", "\"").replace("\\/", "/")
        val r = Regex("""\/\/([\w\.\/])+""")
        val pages = mutableListOf<Page>()
        var index = 0
        r.findAll(pictures).forEach {
            pages.add(Page(index=index, imageUrl="http:${it.value}"))
            index++
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
}