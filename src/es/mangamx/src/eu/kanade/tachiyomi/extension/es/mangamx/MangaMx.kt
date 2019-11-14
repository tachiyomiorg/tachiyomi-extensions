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
import java.util.*

class MangaMx : ParsedHttpSource() {

    override val name = "MangaMx"
    override val baseUrl = "https://manga-mx.com"
    override val lang = "es"
    override val supportsLatest = true

    override fun popularMangaSelector() = "article[id=item]"
    override fun latestUpdatesSelector() = "div.manga-item"
    override fun searchMangaSelector() = "article[id=item]"
    override fun chapterListSelector() = throw Exception ("Not Used")

    override fun popularMangaNextPageSelector() = "a[href*=directorio]:containsOwn(Última)"
    override fun latestUpdatesNextPageSelector() = "a[href*=reciente]:containsOwn(Última)"
    override fun searchMangaNextPageSelector() = "a[href*=/?s]:containsOwn(Última), a[href*=directorio]:containsOwn(Última)"


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/directorio/?orden=visitas&p=$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/reciente/capitulos?p=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = if (query.isNotBlank()) {
            Uri.parse(baseUrl).buildUpon()
                .appendQueryParameter("s", query)
        } else {
            val uri = Uri.parse("$baseUrl/directorio").buildUpon()
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.appendQueryParameter("p", page.toString())
        }
        return GET(uri.toString(), headers)
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

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first().attr("abs:href"))
        manga.title = element.select("a").first().text().trim()
        return manga
    }
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

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: ¡Ignorado si usa la búsqueda de texto!"),
        Filter.Separator(),
        GenreFilter(),
        LetterFilter(),
        StatusFilter(),
        TypeFilter(),
        AdultFilter(),
        SortFilter()
    )

    private class GenreFilter : UriPartFilter("Género", "genero", arrayOf(
        Pair("all","All"),
        Pair("3","Acción"),
        Pair("35","Artes Marciales"),
        Pair("7","Aventura"),
        Pair("31","Ciencia ficción"),
        Pair("1","Comedia"),
        Pair("37","Demonios"),
        Pair("10","Deportes"),
        Pair("2","Drama"),
        Pair("6","Ecchi"),
        Pair("42","Eroge"),
        Pair("4","Escolar"),
        Pair("12","Fantasía"),
        Pair("20","Ficción"),
        Pair("14","Gore"),
        Pair("21","Harem"),
        Pair("27","Histórico"),
        Pair("36","Horror"),
        Pair("43","Isekai"),
        Pair("33","Josei"),
        Pair("34","Magia"),
        Pair("13","Mecha"),
        Pair("41","Militar"),
        Pair("17","Misterio"),
        Pair("30","Músical"),
        Pair("11","Psicológico"),
        Pair("39","Recuentos de la vida"),
        Pair("5","Romance"),
        Pair("19","Seinen"),
        Pair("9","Shōjo"),
        Pair("32","Shōjo-ai"),
        Pair("8","Shōnen"),
        Pair("40","Shōnen ai"),
        Pair("18","Sobrenatural"),
        Pair("38","Supervivencia"),
        Pair("25","Webtoon"),
        Pair("15","Yaoi"),
        Pair("16","Yuri")
        ))

    private class LetterFilter : UriPartFilter("Letra","letra", arrayOf(
        Pair("all","All"),
        Pair("a","A"),
        Pair("b","B"),
        Pair("c","C"),
        Pair("d","D"),
        Pair("e","E"),
        Pair("f","F"),
        Pair("g","G"),
        Pair("h","H"),
        Pair("i","I"),
        Pair("j","J"),
        Pair("k","K"),
        Pair("l","L"),
        Pair("m","M"),
        Pair("n","N"),
        Pair("o","O"),
        Pair("p","P"),
        Pair("q","Q"),
        Pair("r","R"),
        Pair("s","S"),
        Pair("t","T"),
        Pair("u","U"),
        Pair("v","V"),
        Pair("w","W"),
        Pair("x","X"),
        Pair("y","Y"),
        Pair("z","Z")
        ))

    private class StatusFilter : UriPartFilter("Estado", "estado", arrayOf(
        Pair("all","All"),Pair("1","En desarrollo"), Pair("0","Finalizado")))

    private class TypeFilter : UriPartFilter("Tipo", "tipo", arrayOf(
        Pair("all","All"),
        Pair("0","Manga"),
        Pair("1","Manhwa"),
        Pair("2","One Shot"),
        Pair("3","Manhua"),
        Pair("4","Novela")
        ))

    private class AdultFilter : UriPartFilter("Filtro adulto", "adulto", arrayOf(
        Pair("all","All"),Pair("0","Mostrar solo +18"), Pair("1","No mostrar +18")))

    private class SortFilter : UriPartFilterreq("Sort", "orden", arrayOf(
        Pair("visitas","Visitas"),
        Pair("desc","Descendente"),
        Pair("asc","Ascendente"),
        Pair("lanzamiento","Lanzamiento"),
        Pair("nombre","Nombre")
        ))

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriPartFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                            val firstIsUnspecified: Boolean = true,
                                            defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    private open class UriPartFilterreq(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }
}

