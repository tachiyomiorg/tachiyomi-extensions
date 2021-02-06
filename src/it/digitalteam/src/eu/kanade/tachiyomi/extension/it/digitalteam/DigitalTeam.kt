package eu.kanade.tachiyomi.extension.it.digitalteam

import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class DigitalTeam : ParsedHttpSource() {

    override val name = "DigitalTeam"
    override val baseUrl = "https://dgtread.com"
    override val lang = "it"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/reader/series", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("La ricerca è momentaneamente disabilitata.")

    //    LIST SELECTOR
    override fun popularMangaSelector() = "ul li.manga_block"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    //    ELEMENT
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("src")
        manga.setUrlWithoutDomain(element.select(".manga_title a").first().attr("href"))
        manga.title = element.select(".manga_title a").text()
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not Used")
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not Used")

    //    NEXT SELECTOR
    //  Not needed
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    // ////////////////

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("#manga_left")
        val manga = SManga.create()
        manga.author = infoElement.select(".info_name:contains(Autore)").next()?.text()
        manga.artist = infoElement.select(".info_name:contains(Artista)").next()?.text()
        manga.genre = infoElement.select(".info_name:contains(Genere)").next()?.text()
        manga.status = parseStatus(infoElement.select(".info_name:contains(Status)").next().text())
        manga.description = document.select("div.plot")?.text()
        manga.thumbnail_url = infoElement.select(".cover img").attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("in corso") -> SManga.ONGOING
        element.toLowerCase().contains("completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapter_list ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select(".ch_bottom").first()?.text()?.replace("Pubblicato il ", "")?.let {
            try {
                SimpleDateFormat("dd-MM-yyyy", Locale.ITALY).parse(it).time
            } catch (e: ParseException) {
                SimpleDateFormat("H", Locale.ITALY).parse(it).time
            }
        } ?: 0
        return chapter
    }

    protected fun getXhrPages(script_content: String, title: String): String {
        val xhrHeaders = headersBuilder().add("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")
            .build()

        // This can be improved, i don't know how to do it with Regex
        var infomanga = script_content.substringAfter("m='").substringBefore("'")
        var infochapter = script_content.substringAfter("ch='").substringBefore("'")
        var infoch_sub = script_content.substringAfter("chs='").substringBefore("'")
        val body = RequestBody.create(null, "info[manga]=$infomanga&info[chapter]=$infochapter&info[ch_sub]=$infoch_sub&info[title]=$title")
        return client.newCall(POST("$baseUrl/reader/c_i", xhrHeaders, body))
            .execute()
            .asJsoup().select("body").text()
            .replace("\\", "").removeSurrounding("\"")
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val script_content = document.body()!!.toString().substringAfter("current_page=").substringBefore(";")
        val title = document.select("title").first().text()
        val imagesJsonList = JsonParser().parse(getXhrPages(script_content, title)).asJsonArray
        val image_url = imagesJsonList.get(2).string
        val images_name = imagesJsonList.get(1).asJsonArray
        val images_data = imagesJsonList.get(0).asJsonArray
        images_name.forEachIndexed { index, imagename ->
            val imageUrl =
                "$baseUrl/reader$image_url" +
                    "${images_data.get(index).asJsonObject.get("name").string}" +
                    "${imagename.string}" +
                    "${images_data.get(index).asJsonObject.get("ex").string}"
            pages.add(Page(index, "", imageUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
}
