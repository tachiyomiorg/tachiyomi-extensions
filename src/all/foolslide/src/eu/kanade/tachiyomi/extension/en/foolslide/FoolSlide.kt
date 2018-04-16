package eu.kanade.tachiyomi.extension.all.foolslide

import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


open class FoolSlide(override val name: String, override val baseUrl: String, override val lang: String, val urlModifier: String = "") : ParsedHttpSource() {

    override val supportsLatest = true

    override fun popularMangaSelector() = "div.group"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/directory/$page/", headers)
    }

    override fun latestUpdatesSelector() = "div.group"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl$urlModifier/latest/$page/")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }

        element.select("img").first()?.let {
            manga.thumbnail_url = it.absUrl("src").replace("/thumb_", "/")
        }

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.next"

    override fun latestUpdatesNextPageSelector() = "div.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
                .add("search", query)

        return POST("$baseUrl$urlModifier/search/", headers, form.build())
    }

    override fun searchMangaSelector() = "div.group"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsRequest(manga: SManga)
        = allowAdult(super.mangaDetailsRequest(manga))

    open val mangaDetailsInfoSelector = "div.info"
    open val mangaDetailsThumbnailSelector = "div.thumbnail img"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(mangaDetailsInfoSelector).first().text()

        val manga = SManga.create()
        manga.author = infoElement.substringAfter("Author:").substringBefore("Artist:")
        manga.artist = infoElement.substringAfter("Artist:").substringBefore("Synopsis:")
        manga.description = infoElement.substringAfter("Synopsis:")
        manga.thumbnail_url = document.select(mangaDetailsThumbnailSelector).first()?.absUrl("src")

        return manga
    }

    /**
     * Transform a GET request into a POST request that automatically authorizes all adult content
     */
    private fun allowAdult(request: Request): Request {
        return POST(request.url().toString(), body = FormBody.Builder()
                .add("adult", "true")
                .build())
    }

    override fun chapterListRequest(manga: SManga)
        = allowAdult(super.chapterListRequest(manga))

    override fun chapterListSelector() = "div.group div.element, div.list div.element"

    open val chapterDateSelector = "div.meta_r"

    open val chapterUrlSelector = "a[title]"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(chapterUrlSelector).first()
        val dateElement = element.select(chapterDateSelector).first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = dateElement.text()?.let { parseChapterDate(it.substringAfter(", ")) } ?: 0
        return chapter
    }

    open fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy.MM.dd", Locale.US).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter)
        = allowAdult(super.pageListRequest(chapter))

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val jsonstr = doc.substringAfter("var pages = ").substringBefore(";")
        val json = JsonParser().parse(jsonstr).asJsonArray
        val pages = mutableListOf<Page>()
        json.forEach {
            // Create dummy element to resolve relative URL
            val absUrl = document.createElement("a")
                    .attr("href", it["url"].asString)
                    .absUrl("href")

            pages.add(Page(pages.size, "", absUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

}