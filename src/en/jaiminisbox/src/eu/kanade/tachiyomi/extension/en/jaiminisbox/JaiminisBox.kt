package eu.kanade.tachiyomi.extension.en.jaiminisbox

import android.util.Base64
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

class JaiminisBox : ParsedHttpSource() {

    override val name = "Jaimini's Box"

    override val baseUrl = "https://jaiminisbox.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = "div.group"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/reader/directory/$page/", headers)
    }

    override fun latestUpdatesSelector() = "div.group"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/reader/latest/$page/")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a[title]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }

        element.select("img").first().let {
            manga.thumbnail_url = it.attr("src").replace("/thumb_", "/")
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
        val form = FormBody.Builder().apply {
            add("search", query)
        }

        return POST("$baseUrl/reader/search/", headers, form.build())
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

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.info").first().text()

        val manga = SManga.create()
        manga.author = infoElement.substringAfter("Author:").substringBefore("Artist:")
        manga.artist = infoElement.substringAfter("Artist:").substringBefore("Synopsis:")
        manga.description = infoElement.substringAfter("Synopsis:")
        manga.thumbnail_url = document.select("div.thumbnail img").first()?.attr("src")
        return manga
    }

    override fun chapterListSelector() = "div.group div.element"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a[title]").first()
        val dateElement = element.select("div.meta_r").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = dateElement.text()?.let { parseChapterDate(it.substringAfter(", ")) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy.MM.dd").parse(date).time
        } catch (e: ParseException) {
            0L
        }

    }

    override fun pageListParse(document: Document): List<Page> {

        val doc = document.toString()

        val base64Json = doc.substringAfter("JSON.parse(atob(\"").substringBefore("\"));")
        val decodeJson = String(Base64.decode(base64Json, Base64.DEFAULT))
        val json = JsonParser().parse(decodeJson).asJsonArray
        val pages = mutableListOf<Page>()
        json.forEach {
            pages.add(Page(pages.size, "", it.get("url").asString))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

}