package eu.kanade.tachiyomi.extension.fr.scanmanga

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import rx.Observable
import kotlin.random.Random

class ScanManga : ParsedHttpSource() {

    override val name = "Scan-Manga"

    override val baseUrl = "https://www.scan-manga.com"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor { chain ->
            val originalCookies = chain.request().header("Cookie") ?: ""
            val newReq = chain
                .request()
                .newBuilder()
                .header("Cookie", "$originalCookies; _ga=GA1.2.${shuffle("123456789")}.${System.currentTimeMillis() / 1000}")
                .build()
            chain.proceed(newReq)
        }.build()!!

    private val gson = Gson()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/Tout-le-TOP.html", headers)
    }

    override fun popularMangaSelector() = "div.image_manga a[href]"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("img").attr("title")
            setUrlWithoutDomain(element.attr("href"))
            thumbnail_url = element.select("img").attr("data-original")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "#content_news .listing"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("a.nom_manga").text()
            setUrlWithoutDomain(element.select("a.nom_manga").attr("href"))
            /*thumbnail_url = element.select(".logo_manga img").let {
                if (it.hasAttr("data-original"))
                    it.attr("data-original") else it.attr("src")
            }*/ // Better not use it, width is too large, which results in terrible image
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromJson(response)

    fun shuffle(s: String?): String? {
        val result = StringBuffer(s!!)
        var n = result.length
        while (n > 1) {
            val randomPoint: Int = Random.nextInt(n)
            val randomChar = result[randomPoint]
            result.setCharAt(n - 1, randomChar)
            n--
        }
        return result.toString()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchHeaders = headersBuilder().apply {
            add("Referer", "$baseUrl/scanlation/liste_series.html")
            add("x-requested-with", "XMLHttpRequest")
        }.build()
        return GET("$baseUrl/scanlation/scan.data.json", searchHeaders)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        return MangasPage(parseMangaFromJson(response).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    private fun parseMangaFromJson(response: Response): MangasPage {
        val jsonData = response.body!!.string()!!
        if (jsonData == "") {
            return MangasPage(listOf<SManga>(), false)
        }

        val jsonObject = JsonParser().parse(jsonData).asJsonObject

        val mangas = jsonObject.keySet()
            .map { key ->
                // "95","%24100-is-Too-Cheap","0","3","One Shot","","2 avril 2010","","335","178","4010",""
                SManga.create().apply {
                    url = "/" + jsonObject[key][0].string + "/" + jsonObject[key][1].string + ".html"
                    title = Parser.unescapeEntities(key, false)
                    genre = jsonObject[key][2].string.let {
                        when {
                            it.contains("0") -> "Shōnen"
                            it.contains("1") -> "Shōjo"
                            it.contains("2") -> "Seinen"
                            it.contains("3") -> "Josei"
                            else -> null
                        }
                    }
                    status = jsonObject[key][3].string.let {
                        when {
                            it.contains("0") -> SManga.ONGOING // En cours
                            it.contains("1") -> SManga.ONGOING // En pause
                            it.contains("2") -> SManga.COMPLETED // Terminé
                            it.contains("3") -> SManga.COMPLETED // One shot
                            else -> SManga.UNKNOWN
                        }
                    }
                }
            }

        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h2[itemprop=\"name\"]").text()
            author = document.select("li[itemprop=\"author\"] a").joinToString { it.text() }
            description = document.select("p[itemprop=\"description\"]").text()
            thumbnail_url = document.select(".contenu_fiche_technique .image_manga img").attr("src")
        }
    }

    // Chapters
    override fun chapterListSelector() = throw Exception("Not used")
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.texte_volume_manga ul li.chapitre div.chapitre_nom a").map {
            SChapter.create().apply {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
                scanlator = document.select("li[itemprop=\"translator\"] a").joinToString { it.text() }
            }
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val docString = document.toString()

        var lelUrl = Regex("""['"](http.*?scanmanga.eu.*)['"]""").find(docString)?.groupValues?.get(1)
        if (lelUrl == null) {
            lelUrl = Regex("""['"](http.*?le[il].scan-manga.com.*)['"]""").find(docString)?.groupValues?.get(1)
        }

        return Regex("""["'](.*?zoneID.*?pageID.*?siteID.*?)["']""").findAll(docString).toList().mapIndexed { i, pageParam ->
            Page(i, document.location(), lelUrl + pageParam.groupValues[1])
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Referer", page.url)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
