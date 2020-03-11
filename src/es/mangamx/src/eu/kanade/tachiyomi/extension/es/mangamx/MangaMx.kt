package eu.kanade.tachiyomi.extension.es.mangamx


import android.net.Uri
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

open class MangaMx : ParsedHttpSource() {

    //Info
    override val name = "MangaMx"
    override val baseUrl = "https://manga-mx.com"
    override val lang = "es"
    override val supportsLatest = true
    private var csrfToken = ""

    //Popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directorio?filtro=visitas&p=$page", headers)
    override fun popularMangaNextPageSelector() = ".page-item a[rel=next]"
    override fun popularMangaSelector() = "#article-div a"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.select("img").attr("src")
        title = element.select("div:eq(1)").text().trim()
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        csrfToken = document.select("meta[name=csrf-token]").attr("content")

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    //Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/recientes?p=$page", headers)
    /*
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .distinctBy { it.select("a").first().attr("abs:href") }
            .map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null
        return MangasPage(mangas, hasNextPage)
    }

     */

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = "div._1bJU3"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("src")
        element.select("div a").apply {
            title = this.text().trim()
            setUrlWithoutDomain(this.attr("href"))
        }
    }

    //Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("buscar", query)
                .add("_token", csrfToken)
                .build()
            val searchHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").add("Referer", baseUrl).build()
            return POST("$baseUrl/buscar", searchHeaders, formBody )
        } else {
            val uri = Uri.parse("$baseUrl/directorio").buildUpon()
            //Append uri filters
           for (filter in filters) {
               when (filter) {
                   is StatusFilter -> uri.appendQueryParameter(filter.name.toLowerCase(Locale.ROOT), statusArray[filter.state].second)
                   is FilterFilter -> uri.appendQueryParameter(filter.name.toLowerCase(Locale.ROOT), filterArray[filter.state].second)
                   is TypeFilter -> uri.appendQueryParameter(filter.name.toLowerCase(Locale.ROOT), typedArray[filter.state].second)
                   is AdultFilter -> uri.appendQueryParameter(filter.name.toLowerCase(Locale.ROOT), adultArray[filter.state].second)
                   is OrderFilter -> uri.appendQueryParameter(filter.name.toLowerCase(Locale.ROOT), orderArray[filter.state].second)
               }
           }
            uri.appendQueryParameter("p", page.toString())
            return GET(uri.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.isSuccessful) throw Exception("Búsqueda fallida ${response.code()}")
        if ("directorio" in response.request().url().toString()) {
            val document = response.asJsoup()
            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }

            val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        } else {
            val body = response.body()!!.string()
            if (body == "[]") throw Exception("Término de búsqueda demasiado corto")
            val json = JsonParser().parse(body)["mangas"].asJsonArray

            val mangas = json.map { jsonElement -> searchMangaFromJson(jsonElement)   }
            val hasNextPage = false
            return MangasPage(mangas, hasNextPage)
        }
    }
    private fun searchMangaFromJson(jsonElement: JsonElement): SManga = SManga.create().apply {
        title = jsonElement["nombre"].string
        setUrlWithoutDomain(jsonElement["url"].string)
        thumbnail_url = jsonElement["img"].string.replace("/thumb","/cover")
    }

    /* Common
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        manga.title = element.select("h2").text().trim()
        //manga.thumbnail_url = "https:" + element.select("img").attr("src")
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

     */

    //Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img[src*=cover]").attr("abs:src")
        manga.description = document.select("div#sinopsis").last().ownText()
        manga.author = document.select("div#info-i").text().let {
            if (it.contains("Autor", true)) {
                it.substringAfter("Autor:").substringBefore("Fecha:").trim()
            } else "N/A"
        }
        manga.artist = manga.author
        manga.genre = document.select("div#categ a").joinToString(", "){ it.text() }
        manga.status = when (document.select("span#desarrollo")?.first()?.text()) {
            "En desarrollo" -> SManga.ONGOING
            //"Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    //Chapters

    override fun chapterListSelector(): String = "div#c_list a"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text().trim()
        setUrlWithoutDomain(element.attr("href"))
        chapter_number = element.select("span").attr("data-num").toFloat()
        date_upload = parseDate(element.select("span").attr("datetime"))
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US ).parse(date)?.time ?: 0
    }

    /*
    override fun chapterListRequest(manga: SManga): Request {
        val body = FormBody.Builder()
            .addEncoded("cap_list","")
            .build()
        val headers = headersBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()
        return POST(baseUrl + manga.url, headers, body)
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

     */

    //Pages

    /*
    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val body = response.asJsoup()
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
    */
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val script = document.select("script:containsData(hojas)").html()
        val dir = script.substringAfter("var dir = '").substringBefore("';")
        val imgList = script.substringAfter("var hojas = [\"").substringBefore("\"];").split("\",\"")
        imgList.forEach {
            add(Page(size, "", dir+it))
        }
    }
    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    //Filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: ¡Ignorado si usa la búsqueda de texto!"),
        Filter.Separator(),
        StatusFilter("Estado", statusArray),
        FilterFilter("Filtro", filterArray),
        TypeFilter("Tipo", typedArray),
        AdultFilter("Adulto", adultArray),
        OrderFilter("Orden", orderArray)
    )


    private class StatusFilter(name: String, values: Array<Pair<String, String>>) : Filter.Select<String>(name, values.map { it.first }.toTypedArray())
    private class FilterFilter(name: String, values: Array<Pair<String, String>>) : Filter.Select<String>(name, values.map { it.first }.toTypedArray())
    private class TypeFilter(name: String, values: Array<Pair<String, String>>) : Filter.Select<String>(name, values.map { it.first }.toTypedArray())
    private class AdultFilter(name: String, values: Array<Pair<String, String>>) : Filter.Select<String>(name, values.map { it.first }.toTypedArray())
    private class OrderFilter(name: String, values: Array<Pair<String, String>>) : Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private val statusArray = arrayOf(
        Pair("Estado","false"),
        Pair("En desarrollo","1"),
        Pair("Completo","0")
    )
    private val filterArray = arrayOf(
        Pair("Visitas","visitas"),
        Pair("Recientes","id"),
        Pair("Alfabético","nombre")
    )
    private val typedArray = arrayOf(
        Pair("Todo","false"),
        Pair("Mangas","0"),
        Pair("Manhwas","1"),
        Pair("One Shot","2"),
        Pair("Manhuas","3"),
        Pair("Novelas","4")
    )
    private val adultArray = arrayOf(
        Pair("Filtro adulto","false"),
        Pair("No mostrar +18","0"),
        Pair("Mostrar +18","1")
    )
    private val orderArray = arrayOf(
        Pair("Descendente","desc"),
        Pair("Ascendente","asc")
    )
}

