package eu.kanade.tachiyomi.extension.es.heavenmanga

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.*

class HeavenManga : ParsedHttpSource() {

    override val name = "HeavenManga"

    override val baseUrl = "http://heavenmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/60")
    }


    override fun popularMangaSelector() = ".top.clearfix .ranking"

    override fun latestUpdatesSelector() = "#container .ultimos_epis .not"

    override fun searchMangaSelector() = ".top.clearfix .cont_manga"
    private fun novelaFilterSelector() = ".lstsradd"
    private fun comicFilterSelector() = "section#related"

    override fun chapterListSelector() = "#mamain ul li"

    private fun chapterPageSelector() = "a#l"

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = "li:contains(Siguiente):not([id=inactive])"


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val search_url = "$baseUrl/buscar/$query.html"

        // Filter
        if(query.isBlank()) {
            val ext = ".html"
            var name = ""
            filters.forEach { filter ->
                when(filter) {
                    is GenreFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            return GET("$baseUrl/genero/$name$ext", headers)
                        }
                    }
                    is AlphabeticoFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            return GET("$baseUrl/letra/$name$ext", headers)
                        }
                    }
                    is ListaCompletasFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            return GET("$baseUrl/$name/", headers)
                        }
                    }
                }
            }
    
        }

        return GET(search_url, headers)
    }

    override fun imageUrlRequest(page: Page) = GET(page.url, headers)

    // get contents of a url
    private fun getUrlContents(url: String): Document = client.newCall(GET(url, headers)).execute().asJsoup()


    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            val allElements: Elements = it.select(".box .tit")
            //get all elements under .box .tit
            for (e: Element in allElements) {
                title = e.childNode(0).toString() //the title
            }
            thumbnail_url = it.select(".box img").attr("src")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            val latestChapter = getUrlContents(it.attr("href"))
            val url = latestChapter.select(".rpwe-clearfix:last-child a")
            setUrlWithoutDomain(url.attr("href"))
            title = it.select("span span").text()
            thumbnail_url = it.select("img").attr("src")
        }
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val figure = element.select("figure").attr("style")
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = if(element.hasClass("titnom")) element.select(".titnom").text() else it.select("header").text()
            thumbnail_url = if(element.select("figure").hasAttr("style")) figure.substring(figure.indexOf("http"), figure.indexOf(")") ) else it.select("img").attr("src")
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val timeElement = element.select("span").first()
        val time = timeElement.text()
        val date = time.replace("--", "-")
        val url = urlElement.attr("href")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(url)
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(date.toString())
        return chapter
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var el: String

        if(document.select(novelaFilterSelector()).first() != null) {
            el = novelaFilterSelector()
        } else if (document.select(comicFilterSelector()).first() != null) {
            el = comicFilterSelector()
        } else {
            el = searchMangaSelector()
        }

        val mangas = document.select(el).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }
    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        document.select(".left.home").let {
            val genres = it.select(".sinopsis a")?.map {
                it.text()
            }

            genre = genres?.joinToString(", ")
            val allElements: Elements = document.select(".sinopsis")
            //get all elements under .sinopsis
            for (e: Element in allElements) {
                description = e.childNode(0).toString() //the description
            }
        }

        thumbnail_url = document.select(".cover.clearfix img[style='width:142px;height:212px;']").attr("src")
    }

    private fun parseChapterDate(date: String): Long = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(date).time

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()
        document.select(chapterListSelector()).forEach {
            chapters.add(chapterFromElement(it))
        }
        return chapters
    }


    override fun imageUrlParse(document: Document) = document.select("#p").attr("src").toString()

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val leerUrl = document.select(chapterPageSelector()).attr("href")
        val urlElement = getUrlContents(leerUrl)
        urlElement.body().select("option").forEach {
            pages.add(Page(pages.size, it.attr("value")))
        }
        pages.getOrNull(0)?.imageUrl = imageUrlParse(urlElement)

        return pages
    }

    /**
     * Array.from(document.querySelectorAll('.categorias a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on http://heavenmanga.com/top/
     * */
    private class GenreFilter : UriPartFilter("Géneros", arrayOf(
        Pair("Todo", ""),
        Pair("Accion", "accion"),
        Pair("Adulto", "adulto"),
        Pair("Aventura", "aventura"),
        Pair("Artes Marciales", "artes+marciales"),
        Pair("Acontesimientos de la Vida", "acontesimientos+de+la+vida"),
        Pair("Bakunyuu", "bakunyuu"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Comic", "comic"),
        Pair("Combate", "combate"),
        Pair("Comedia", "comedia"),
        Pair("Cooking", "cooking"),
        Pair("Cotidiano", "cotidiano"),
        Pair("Colegialas", "colegialas"),
        Pair("Critica social", "critica+social"),
        Pair("Ciencia ficcion", "ciencia+ficcion"),
        Pair("Cambio de genero", "cambio+de+genero"),
        Pair("Cosas de la Vida", "cosas+de+la+vida"),
        Pair("Drama", "drama"),
        Pair("Deporte", "deporte"),
        Pair("Doujinshi", "doujinshi"),
        Pair("Delincuentes", "delincuentes"),
        Pair("Ecchi", "ecchi"),
        Pair("Escolar", "escolar"),
        Pair("Erotico", "erotico"),
        Pair("Escuela", "escuela"),
        Pair("Estilo de Vida", "estilo+de+vida"),
        Pair("Fantasia", "fantasia"),
        Pair("Fragmentos de la Vida", "fragmentos+de+la+vida"),
        Pair("Gore", "gore"),
        Pair("Gender Bender", "gender+bender"),
        Pair("Humor", "humor"),
        Pair("Harem", "harem"),
        Pair("Haren", "haren"),
        Pair("Hentai", "hentai"),
        Pair("Horror", "horror"),
        Pair("Historico", "historico"),
        Pair("Josei", "josei"),
        Pair("Loli", "loli"),
        Pair("Light", "light"),
        Pair("Lucha Libre", "lucha+libre"),
        Pair("Manga", "manga"),
        Pair("Mecha", "mecha"),
        Pair("Magia", "magia"),
        Pair("Maduro", "maduro"),
        Pair("Manhwa", "manhwa"),
        Pair("Manwha", "manwha"),
        Pair("Mature", "mature"),
        Pair("Misterio", "misterio"),
        Pair("Mutantes", "mutantes"),
        Pair("Novela", "novela"),
        Pair("Orgia", "orgia"),
        Pair("OneShot", "oneshot"),
        Pair("OneShots", "oneshots"),
        Pair("Psicologico", "psicologico"),
        Pair("Romance", "romance"),
        Pair("Recuentos de la vida", "recuentos+de+la+vida"),
        Pair("Smut", "smut"),
        Pair("Shojo", "shojo"),
        Pair("Shonen", "shonen"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Suspenso", "suspenso"),
        Pair("School Life", "school+life"),
        Pair("Sobrenatural", "sobrenatural"),
        Pair("SuperHeroes", "superheroes"),
        Pair("Supernatural", "supernatural"),
        Pair("Slice of Life", "slice+of+life"),
        Pair("Super Poderes", "ssuper+poderes"),
        Pair("Terror", "terror"),
        Pair("Torneo", "torneo"),
        Pair("Tragedia", "tragedia"),
        Pair("Transexual", "transexual"),
        Pair("Vida", "vida"),
        Pair("Vampiros", "vampiros"),
        Pair("Violencia", "violencia"),
        Pair("Vida Pasada", "vida+pasada"),
        Pair("Vida Cotidiana", "vida+cotidiana"),
        Pair("Vida de Escuela", "vida+de+escuela"),
        Pair("Webtoon", "webtoon"),
        Pair("Webtoons", "webtoons"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri")
    ))

    /**
     * Array.from(document.querySelectorAll('.letras a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on http://heavenmanga.com/top/
     * */
    private class AlphabeticoFilter : UriPartFilter("Alfabético", arrayOf(
        Pair("Todo", ""),
        Pair("A", "a"),
        Pair("B", "b"),
        Pair("C", "c"),
        Pair("D", "d"),
        Pair("E", "e"),
        Pair("F", "f"),
        Pair("G", "g"),
        Pair("H", "h"),
        Pair("I", "i"),
        Pair("J", "j"),
        Pair("K", "k"),
        Pair("L", "l"),
        Pair("M", "m"),
        Pair("N", "n"),
        Pair("O", "o"),
        Pair("P", "p"),
        Pair("Q", "q"),
        Pair("R", "r"),
        Pair("S", "s"),
        Pair("T", "t"),
        Pair("U", "u"),
        Pair("V", "v"),
        Pair("W", "w"),
        Pair("X", "x"),
        Pair("Y", "y"),
        Pair("Z", "z"),
        Pair("0-9", "0-9")
    ))

    /**
     * Array.from(document.querySelectorAll('#t li a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on http://heavenmanga.com/top/
     * */
    private class ListaCompletasFilter: UriPartFilter("Lista Completa", arrayOf(
        Pair("Todo", ""),
        Pair("Lista Comis", "comic"),
        Pair("Lista Novelas", "novela"),
        Pair("Lista Adulto", "adulto")
    ))

    override fun getFilterList() = FilterList(
        // Search and filter don't work at the same time
        Filter.Header("NOTA: Los filtros se ignoran si se utiliza la búsqueda de texto."),
        Filter.Header("Sólo se puede utilizar un filtro a la vez."),
        Filter.Separator(),
        GenreFilter(),
        AlphabeticoFilter(),
        ListaCompletasFilter()
    )


    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
    
}
