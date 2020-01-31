package eu.kanade.tachiyomi.extension.en.mangalinkz

import android.util.Base64
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaLinkz : ParsedHttpSource() {

    override val name = "Manga Linkz"

    override val baseUrl = "https://mangalinkz.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular", headers)
    }

    override fun popularMangaSelector() = "div#content div.card"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.mb-1 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return (GET("$baseUrl/latest", headers))
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return document.select("div#series div.container").let { info ->
            SManga.create().apply {
                title = info.select("h1").text()
                author = info.select("p:containsOwn(Authors:) a").text()
                status = info.select("p:containsOwn(Status:)").text().substringAfter(": ").toStatus()
                genre = info.select("p:containsOwn(Genre) a").joinToString { it.text() }
                description = info.select("p").last().text()
                thumbnail_url = info.select("img").attr("abs:src")
            }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.px-sm-0 > table tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("td.text-nowrap").text().toChapterDate()
        }
    }

    val calendar by lazy { Calendar.getInstance() }

    private fun String?.toChapterDate(): Long {
        return when {
            this == null -> 0L
            this.contains("just now", ignoreCase = true) -> calendar.timeInMillis
            this.endsWith("ago", ignoreCase = true) -> {
                this.split(" ").let {
                    when (it[1].removeSuffix("s")) {
                        "hr" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -it[0].toInt()) }.timeInMillis
                        "day" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -it[0].toInt()) }.timeInMillis
                        else -> 0L
                    }
                }
            }
            else -> try {
                SimpleDateFormat("MMM d, yyyy", Locale.US).parse(this).time
            } catch (_: Exception) {
                0L
            }
        }
    }

    // Pages

    private val gson by lazy { Gson() }

    override fun pageListParse(document: Document): List<Page> {
        val encoded = document.select("script:containsData(atob)").first().data()
            .substringAfter("atob(\"").substringBefore("\"")
        val decoded = Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8).removeSurrounding("[", "]")

        return gson.fromJson<JsonObject>(decoded)["pages"].asJsonArray.mapIndexed{ i, jsonElement -> Page(i, "", jsonElement.string) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

}
