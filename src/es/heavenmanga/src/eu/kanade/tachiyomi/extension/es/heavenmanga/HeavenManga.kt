package eu.kanade.tachiyomi.extension.es.heavenmanga

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.jsoup.nodes.Element
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

    override fun chapterListSelector() = "#mamain ul li"

    private fun chapterPageSelector() = "a#l"

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val search_url = "$baseUrl/buscar/$query.html"

        // Filter
        if(query.isBlank()) {
            var url = ""
            filters.forEach { filter ->
                when(filter) {
                    is GenreFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            url = baseUrl + filter.toUriPart()
                            return GET(url, headers)
                        }
                    }
                    is AlphabeticoFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            url = baseUrl + filter.toUriPart()
                            return GET(url, headers)
                        }
                    }
                    is ListaCompletasFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            url = filter.toUriPart()
                            return GET(url, headers)
                        }
                    }
                }
            }

        }

        return GET(search_url, headers)
    }

    override fun imageUrlRequest(page: Page) = GET(page.url, headers)

    // get contents of a url
    private fun getUrlContents(url: String): Document = Jsoup.connect(url).timeout(0).get()


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
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("header").text()
            thumbnail_url = it.select("img").attr("src")
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val timeElement = element.select("span").first()
        val time = timeElement.text()
        val date = time.replace("--", "-")
        val url = urlElement.attr("href")

        val chapter = SChapter.create()
        Log.d("HM: chapUrl->", chapter.setUrlWithoutDomain(url).toString())
        chapter.setUrlWithoutDomain(url)
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(date.toString())
        return chapter
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
        Pair("Accion", "/genero/accion.html"),
        Pair("Adulto", "/genero/adulto.html"),
        Pair("Aventura", "/genero/aventura.html"),
        Pair("Artes Marciales", "/genero/artes+marciales.html"),
        Pair("Acontesimientos de la Vida", "/genero/acontesimientos+de+la+vida.html"),
        Pair("Bakunyuu", "/genero/bakunyuu.html"),
        Pair("Sci-fi", "/genero/sci-fi.html"),
        Pair("Comic", "/genero/comic.html"),
        Pair("Combate", "/genero/combate.html"),
        Pair("Comedia", "/genero/comedia.html"),
        Pair("Cooking", "/genero/cooking.html"),
        Pair("Cotidiano", "/genero/cotidiano.html"),
        Pair("Colegialas", "/genero/colegialas.html"),
        Pair("Critica social", "/genero/critica+social.html"),
        Pair("Ciencia ficcion", "/genero/ciencia+ficcion.html"),
        Pair("Cambio de genero", "/genero/cambio+de+genero.html"),
        Pair("Cosas de la Vida", "/genero/cosas+de+la+vida.html"),
        Pair("Drama", "/genero/drama.html"),
        Pair("Deporte", "/genero/deporte.html"),
        Pair("Doujinshi", "/genero/doujinshi.html"),
        Pair("Delincuentes", "/genero/delincuentes.html"),
        Pair("Ecchi", "/genero/ecchi.html"),
        Pair("Escolar", "/genero/escolar.html"),
        Pair("Erotico", "/genero/erotico.html"),
        Pair("Escuela", "/genero/escuela.html"),
        Pair("Estilo de Vida", "/genero/estilo+de+vida.html"),
        Pair("Fantasia", "/genero/fantasia.html"),
        Pair("Fragmentos de la Vida", "/genero/fragmentos+de+la+vida.html"),
        Pair("Gore", "/genero/gore.html"),
        Pair("Gender Bender", "/genero/gender+bender.html"),
        Pair("Humor", "/genero/humor.html"),
        Pair("Harem", "/genero/harem.html"),
        Pair("Haren", "/genero/haren.html"),
        Pair("Hentai", "/genero/hentai.html"),
        Pair("Horror", "/genero/horror.html"),
        Pair("Historico", "/genero/historico.html"),
        Pair("Josei", "/genero/josei.html"),
        Pair("Loli", "/genero/loli.html"),
        Pair("Light", "/genero/light.html"),
        Pair("Lucha Libre", "/genero/lucha+libre.html"),
        Pair("Manga", "/genero/manga.html"),
        Pair("Mecha", "/genero/mecha.html"),
        Pair("Magia", "/genero/magia.html"),
        Pair("Maduro", "/genero/maduro.html"),
        Pair("Manhwa", "/genero/manhwa.html"),
        Pair("Manwha", "/genero/manwha.html"),
        Pair("Mature", "/genero/mature.html"),
        Pair("Misterio", "/genero/misterio.html"),
        Pair("Mutantes", "/genero/mutantes.html"),
        Pair("Novela", "/genero/novela.html"),
        Pair("Orgia", "/genero/orgia.html"),
        Pair("OneShot", "/genero/oneshot.html"),
        Pair("OneShots", "/genero/oneshots.html"),
        Pair("Psicologico", "/genero/psicologico.html"),
        Pair("Romance", "/genero/romance.html"),
        Pair("Recuentos de la vida", "/genero/recuentos+de+la+vida.html"),
        Pair("Smut", "/genero/smut.html"),
        Pair("Shojo", "/genero/shojo.html"),
        Pair("Shonen", "/genero/shonen.html"),
        Pair("Seinen", "/genero/seinen.html"),
        Pair("Shoujo", "/genero/shoujo.html"),
        Pair("Shounen", "/genero/shounen.html"),
        Pair("Suspenso", "/genero/suspenso.html"),
        Pair("School Life", "/genero/school+life.html"),
        Pair("Sobrenatural", "/genero/sobrenatural.html"),
        Pair("SuperHeroes", "/genero/superheroes.html"),
        Pair("Supernatural", "/genero/supernatural.html"),
        Pair("Slice of Life", "/genero/slice+of+life.html"),
        Pair("Super Poderes", "/genero/ssuper+poderes.html"),
        Pair("Terror", "/genero/terror.html"),
        Pair("Torneo", "/genero/torneo.html"),
        Pair("Tragedia", "/genero/tragedia.html"),
        Pair("Transexual", "/genero/transexual.html"),
        Pair("Vida", "/genero/vida.html"),
        Pair("Vampiros", "/genero/vampiros.html"),
        Pair("Violencia", "/genero/violencia.html"),
        Pair("Vida Pasada", "/genero/vida+pasada.html"),
        Pair("Vida Cotidiana", "/genero/vida+cotidiana.html"),
        Pair("Vida de Escuela", "/genero/vida+de+escuela.html"),
        Pair("Webtoon", "/genero/webtoon.html"),
        Pair("Webtoons", "/genero/webtoons.html"),
        Pair("Yaoi", "/genero/yaoi.html"),
        Pair("Yuri", "/genero/yuri.html")
    ))

    /**
     * Array.from(document.querySelectorAll('.letras a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on http://heavenmanga.com/top/
     * */
    private class AlphabeticoFilter : UriPartFilter("Alfabético", arrayOf(
        Pair("Todo", ""),
        Pair("A", "/letra/a.html"),
        Pair("B", "/letra/b.html"),
        Pair("C", "/letra/c.html"),
        Pair("D", "/letra/d.html"),
        Pair("E", "/letra/e.html"),
        Pair("F", "/letra/f.html"),
        Pair("G", "/letra/g.html"),
        Pair("H", "/letra/h.html"),
        Pair("I", "/letra/i.html"),
        Pair("J", "/letra/j.html"),
        Pair("K", "/letra/k.html"),
        Pair("L", "/letra/l.html"),
        Pair("M", "/letra/m.html"),
        Pair("N", "/letra/n.html"),
        Pair("O", "/letra/o.html"),
        Pair("P", "/letra/p.html"),
        Pair("Q", "/letra/q.html"),
        Pair("R", "/letra/r.html"),
        Pair("S", "/letra/s.html"),
        Pair("T", "/letra/t.html"),
        Pair("U", "/letra/u.html"),
        Pair("V", "/letra/v.html"),
        Pair("W", "/letra/w.html"),
        Pair("X", "/letra/x.html"),
        Pair("Y", "/letra/y.html"),
        Pair("Z", "/letra/z.html"),
        Pair("0-9", "/letra/0-9.html")
    ))

    /**
     * Array.from(document.querySelectorAll('#t li a')).map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
     * on http://heavenmanga.com/top/
     * */
    private class ListaCompletasFilter: UriPartFilter("Lista Completa", arrayOf(
        Pair("Todo", ""),
        Pair("Lista Comis", "http://heavenmanga.com/comic/"),
        Pair("Lista Novelas", "http://heavenmanga.com/novela/"),
        Pair("Lista Adulto", "http://heavenmanga.com/adulto/")
    ))

    override fun getFilterList() = FilterList(
        // Search and filter don't work at the same time
        Filter.Header("NOTE: Ignored if using text search!"),
        GenreFilter(),
        AlphabeticoFilter(),
        ListaCompletasFilter()
    )


    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
    
}
