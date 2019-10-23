package eu.kanade.tachiyomi.extension.es.mangamx


import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class MangaMx : ParsedHttpSource() {

    override val name = "MangaMx"
    override val baseUrl = "https://manga-mx.com"
    override val lang = "es"
    override val supportsLatest = true

    override fun popularMangaSelector() = "article[id=item]"
    override fun latestUpdatesSelector() = "article[id=item]"
    override fun searchMangaSelector() = "article[id=item]"
    override fun chapterListSelector() = throw Exception ("Not Used")

    override fun popularMangaNextPageSelector() = "a[href*=directorio]:containsOwn(Última)"
    override fun latestUpdatesNextPageSelector() = "a[href*=reciente]:containsOwn(Última)"
    override fun searchMangaNextPageSelector() = "a[href*=/?s]:containsOwn(Última)"


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directorio/?orden=visitas&p=$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/reciente/mangas?p=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/?s=$query")?.newBuilder()
        return GET(url.toString(), headers)
    }

    //override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    //override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun chapterListRequest(manga: SManga): Request {
        val body = FormBody.Builder()
            .addEncoded("cap_list","")
            .build()
        val headers = headersBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()
        return POST(baseUrl + manga.url, headers, body)
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element)= mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        manga.title = element.select("h2").text().trim()
        //manga.thumbnail_url = "https:" + element.select("img").attr("src")
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }


    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonData = response.body()!!.string()
        val results = JsonParser().parse(jsonData).asJsonArray
        val chapters = mutableListOf<SChapter>()
        val url = "https:" + results[0].string
        for (i in 1 until results.size()) {
            val obj = results[i]
            chapters.add(chapterFromJson(obj, url))
        }
        return chapters
    }

    private fun chapterFromJson (obj: JsonElement, url: String): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(url + obj["id"].string)
        chapter.name = obj["tc"].string + obj["titulo"].string
        chapter.chapter_number = obj["numero"].string.toFloat()
        chapter.date_upload = parseDate(obj["datetime"].string)
        return chapter
    }


    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd kk:mm:ss", Locale.US ).parse(date).time
    }

    override fun chapterFromElement(element: Element)= throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img[src*=cover]").attr("abs:src")
        manga.description = document.select("div[id=sinopsis]").last().ownText()
        manga.author = document.select("div[id=info-i]").text().let {
            if (it.contains("Autor", true)) {
                it.substringAfter("Autor:").substringBefore("Fecha:").trim()
            } else "N/A"
        }
        manga.artist = manga.author
        val glist = document.select("div[id=categ] a[href*=genero]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span[id=desarrollo]")?.first()?.text()) {
            "En desarrollo" -> SManga.ONGOING
            //"Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        val script = body.select("script:containsData(cap_info)").html()
        val jsonData = script.substringAfter("var cap_info = ").substringBeforeLast(";")
        val results = JsonParser().parse(jsonData).asJsonArray
        val jsonImg = results[1].asJsonArray
        val url = "https:" + jsonImg[0].string
        val pages = mutableListOf<Page>()
        for (i in 1 until jsonImg.size()) {
            pages.add(Page(i, "",url + jsonImg[i].string))
        }
        return pages
    }

    override fun pageListParse(document: Document)= throw Exception("Not Used")
    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

}

