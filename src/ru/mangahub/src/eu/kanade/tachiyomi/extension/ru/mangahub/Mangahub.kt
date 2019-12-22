package eu.kanade.tachiyomi.extension.ru.mangahub

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

open class Mangahub : ParsedHttpSource() {

    override val name = "Mangahub"

    override val baseUrl = "https://mangahub.ru"

    override val lang = "ru"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor).build()


    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/explore?filter[sort]=rating&filter[dateStart][left_number]=1900&filter[dateStart][right_number]=2099&page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/explore?filter[sort]=update&filter[dateStart][left_number]=1900&filter[dateStart][right_number]=2099&page=$page", headers)

    override fun popularMangaSelector() = "div.comic-grid-col-xl"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.comic-grid-image").attr("data-background-image")
        manga.title = element.select("a.comic-grid-name").text()
        manga.setUrlWithoutDomain(element.select("a.comic-grid-name").attr("href"))
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "li.next > a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/manga?query=$query&sort=rating_short&page=$page")
    }

    override fun searchMangaSelector() = "div.comic-grid-col-xl"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.comic-grid-image").attr("data-background-image")
        manga.title = element.select("a.comic-grid-name").text()
        manga.setUrlWithoutDomain(element.select("a.comic-grid-name").attr("href"))
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select("a[itemprop]")?.text()
        manga.genre = document.select("div.tag").text().replace(" ", ", ")
        manga.description = document.select("div.markdown-style").text()
        manga.status = parseStatus(document.select("div.sticky-top span.status-label").toString())
        manga.thumbnail_url = document.select("img.cover-detail-img").attr("src")
        return manga
    }

    private fun parseStatus(elements: String): Int = when {
        elements.contains("Переведена") or elements.contains("Выпуск завершен") -> SManga.COMPLETED
        else -> SManga.ONGOING
    }

    override fun chapterListSelector() = "div.py-2.px-3"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("div.align-items-center > a").first()
        val chapter = SChapter.create()
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.text-muted").text()?.let {
            SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(it).time
        } ?: 0
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("(Глава\\s)((\\d|\\.)+)")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[2]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pictures = document.select("div.row > div > div.mb-4").attr("data-js-scans").replace("&quot;", "\"").replace("\\/", "/")
        val r = Regex("""\/\/([\w\.\/])+""")
        val pages = mutableListOf<Page>()
        for ((index, value) in r.findAll(pictures).withIndex()) {
            pages.add(Page(index = index, imageUrl = "http:${value.value}"))
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
