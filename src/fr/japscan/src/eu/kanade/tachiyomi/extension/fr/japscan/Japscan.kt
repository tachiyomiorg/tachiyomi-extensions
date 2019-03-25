package eu.kanade.tachiyomi.extension.fr.japscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.commons.lang3.StringUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat

class Japscan : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Japscan"

    override val baseUrl = "https://www.japscan.to"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val indicator = "&decodeImage"

        val request = chain.request()
        val url = request.url().toString()

        val newRequest = request.newBuilder()
                .url(url.substringBefore(indicator))
                .build()
        val response = chain.proceed(newRequest)

        if (!url.endsWith(indicator)) return@addInterceptor response

        val res = response.body()!!.byteStream().use {
            decodeImage(it)
        }

        val rb = ResponseBody.create(MediaType.parse("image/png"), res)
        response.newBuilder().body(rb).build()
    }.build()

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy")
        }
    }

    override fun popularMangaSelector() = "#top_mangas_week li > span"

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#chapters > div:eq(0) > h3.text-truncate"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()

            val s = StringUtils.stripAccents(it.text())
                    .replace("[\\W]".toRegex(), "-")
                    .replace("[-]{2,}".toRegex(), "-")
                    .replace("^-|-$".toRegex(), "")
            manga.thumbnail_url = "$baseUrl/imgs/mangas/$s.jpg"
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "#theresnone"

    override fun latestUpdatesNextPageSelector() = "#theresnone"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("search", StringUtils.stripAccents(query))
        }
        return POST("$baseUrl/search/", headers, form.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = JsonParser().parse(response.body()!!.string()).asJsonArray

        if (!result!!.isJsonArray)
            return MangasPage(emptyList(), false)

        val searchMangas = result.map {
            searchMangaItemParse(it.asJsonObject)
        }

        return MangasPage(searchMangas, false)
    }

    private fun searchMangaItemParse(obj: JsonObject) = SManga.create().apply {
        title = obj["name"]!!.asString
        thumbnail_url = "$baseUrl/${obj["image"]!!.asString}"
        url = obj["url"]!!.asString
    }

    override fun searchMangaSelector() = "#theresnone"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "#theresnone"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#main > .card > .card-body").first()

        val manga = SManga.create()
        manga.thumbnail_url = "$baseUrl/${infoElement.select(".d-flex > div.m-2:eq(0) > img").attr("src")}"

        infoElement.select(".d-flex > div.m-2:eq(1) > p.mb-2").forEachIndexed { _, el ->
            when (el.select("span").text().trim()) {
                "Auteur(s):" -> manga.author = el.text().replace("Auteur(s):", "").trim()
                "Artiste(s):" -> manga.artist = el.text().replace("Artiste(s):", "").trim()
                "Genre(s):" -> manga.genre = el.text().replace("Genre(s):", "").trim()
                "Statut:" -> manga.status = el.text().replace("Statut:", "").trim().let {
                    parseStatus(it)
                }
            }
        }
        manga.description = infoElement.select("> p").text().orEmpty()

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapters_list > div.collapse > div.chapters_list"+
            ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW)))"
    //JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs and replace it with a translation as soon as available.
    //Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.


    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.ownText()
        //Using ownText() doesn't include childs' text, like "VUS" or "RAW" badges, in the chapter name.
        chapter.date_upload = element.select("> span").text().trim().let { parseChapterDate(it) }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val imagePath = "(.*\\/).*".toRegex().find(document.select("#image").attr("data-src"))
        val imageScrambled = if (!document.select("script[src^='/js/iYFbYi_']").isNullOrEmpty()) "&decodeImage" else ""

        document.select("select#pages").first()?.select("option")?.forEach {
            pages.add(Page(pages.size, "", "${imagePath?.groupValues?.get(1)}${it.attr("data-img")}$imageScrambled"))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun decodeImage(img: InputStream): ByteArray {
        val input = BitmapFactory.decodeStream(img)

        val xResult = Bitmap.createBitmap(input.width,
                input.height,
                Bitmap.Config.ARGB_8888)
        val xCanvas = Canvas(xResult)

        val result = Bitmap.createBitmap(input.width,
                input.height,
                Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (x in 0..input.width step 200) {
            val col1 = Rect(x, 0, x + 100, input.height)
            if ((x + 200) < input.width) {
                val col2 = Rect(x + 100, 0, x + 200, input.height)
                xCanvas.drawBitmap(input, col1, col2, null)
                xCanvas.drawBitmap(input, col2, col1, null)
            } else {
                val col2 = Rect(x + 100, 0, input.width, input.height)
                xCanvas.drawBitmap(input, col1, col1, null)
                xCanvas.drawBitmap(input, col2, col2, null)
            }
        }

        for (y in 0..input.height step 200) {
            val row1 = Rect(0, y, input.width, y + 100)

            if ((y + 200) < input.height) {
                val row2 = Rect(0, y + 100, input.width, y + 200)
                canvas.drawBitmap(xResult, row1, row2, null)
                canvas.drawBitmap(xResult, row2, row1, null)
            } else {
                val row2 = Rect(0, y + 100, input.width, input.height)
                canvas.drawBitmap(xResult, row1, row1, null)
                canvas.drawBitmap(xResult, row2, row2, null)
            }
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }
}
