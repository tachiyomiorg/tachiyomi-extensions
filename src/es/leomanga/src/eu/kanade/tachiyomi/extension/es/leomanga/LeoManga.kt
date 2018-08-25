package eu.kanade.tachiyomi.extension.es.leomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.Jsoup
import java.util.*

class LeoManga : ParsedHttpSource() {

    override val name = "LeoManga"

    override val baseUrl = "http://leomanga.com"

    override val lang = "es"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "ul.list-inline > li.manga-all"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int)
            = GET("$baseUrl/directorio-manga?pagina=$page", headers)

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a").let {
            manga.setUrlWithoutDomain(baseUrl + it.attr("href"))
            manga.title = it.select("h2.title-dirmanga").toString().substringAfter(">").substringBefore("<br>")
            manga.thumbnail_url = baseUrl + it.select("div.image-dir > img").attr("data-original").toString()
        }

        return manga
    }

    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("p.text-justify").text()
        status = document.select("div.downstate")?.text().orEmpty().let {parseStatus(it)}
        thumbnail_url = baseUrl + document.select("div.manga-right > div.well-image > img")?.attr("data-original")
        genre = document.select("div#page-manga > div.row").first().select("div.col-sm-4").map {
                it.text().substringAfter("Géneros:")
        }.joinToString(", ")
        author = document.select("div.col-sm-auth").text().substringAfter("Autor:")
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Curso") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li > a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    /*
    * LEOMANGA UTILIZA DOS BUSCADORES DISTINTOS, UNO PARA LOS FILTROS Y OTRO PARA QUERYS.
    * ADEMAS MUESTRA LOS RESULTADOS DE FORMA DISTINTA
    */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // URL PARA BUSCAR POR ESTADO, ETIQUETA, DEMOGRAFIA Y ESTILO.
        var url = HttpUrl.parse("$baseUrl/directorio-manga")?.newBuilder()!!
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Types -> url.addQueryParameter("estilo", arrayOf("", "manga", "manhwa", "manhua")[filter.state])
                is Status -> url.addQueryParameter("estado", arrayOf("", "finalizado", "en-curso")[filter.state])
                is Demography -> url.addQueryParameter("demografia", arrayOf("", "shonen", "seinen", "shojo", "josei", "kodomo", "yuri", "yaoi")[filter.state])
                is Genres -> url.addQueryParameter("genero", arrayOf(
                    "",
                    "accion",
                    "artes-marciales",
                    "aventura",
                    "artes-marciales",
                    "comedia",
                    "deporte",
                    "doujinshi",
                    "drama",
                    "ecchi",
                    "escolar",
                    "fantasia",
                    "gender-bender",
                    "gore",
                    "harem",
                    "historico",
                    "horror",
                    "lolicon",
                    "magia",
                    "mecha",
                    "misterio",
                    "musical",
                    "one-shot",
                    "parodia",
                    "policiaca",
                    "psicologia",
                    "romance",
                    "shojo-ai",
                    "shonen-ai",
                    "shota",
                    "slice-of-life",
                    "smut",
                    "sobrenatural",
                    "superpoderes",
                    "tragedia"
                )[filter.state])
            }
        }

        // URL PARA BUSCAR POR QUERYS.
        if(query.isNotEmpty()){
            url = HttpUrl.parse("$baseUrl/buscar")?.newBuilder()!!.addQueryParameter("s", query)
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table.manga-searchtable > tbody > tr:has(td:gt(1)), ul.list-inline > li.manga-all"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val url = element.baseUri()

        //SELECTOR PARA RESULTADOS DE BUSQUEDA POR ETIQUETA, ESTADO, DEMOGRAFIA Y ARTISTAS.
        if(url.contains("directorio-manga", ignoreCase = false)) {
            element.select("a").let {
                setUrlWithoutDomain(baseUrl + it.attr("href"))
                title = it.select("h2.title-dirmanga").toString().substringAfter(">").substringBefore("<br>")
                thumbnail_url = baseUrl + it.select("div.image-dir > img").attr("data-original").toString()
            }
        }

        //SELECTOR PARA RESULTADOS DE BUSQUEDAS POR QUERY.
        if (url.contains("buscar", ignoreCase = false)) {
            element.select("td").first()?.let {
                setUrlWithoutDomain(it.attr("onclick").substringAfter("location=\"").substringBefore("\""))
                title = it.select("div.title-searchmanga").text()
                thumbnail_url = baseUrl + it.select("div.big-imgsearch > div.lit-imgsearch > img").attr("onerror").substringAfter("src='").substringBefore("'")
            }
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun chapterListSelector() = "ul.ul-chapter > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text()
        date_upload = element.select("div.right-date").last()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")

        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[1])
            val dates: Calendar = Calendar.getInstance()

            if (dateWords[2].contains("segundo")) {
                dates.add(Calendar.SECOND, -timeAgo)
            } else if (dateWords[2].contains("segundos")) {
                dates.add(Calendar.SECOND, -timeAgo)
            }

            else if (dateWords[2].contains("minuto")) {
                dates.add(Calendar.MINUTE, -timeAgo)
            } else if (dateWords[2].contains("minutos")) {
                dates.add(Calendar.MINUTE, -timeAgo)
            }

            else if (dateWords[2].contains("hora")) {
                dates.add(Calendar.HOUR_OF_DAY, -timeAgo)
            } else if (dateWords[2].contains("horas")) {
                dates.add(Calendar.HOUR_OF_DAY, -timeAgo)
            }

            else if (dateWords[2].contains("dia")) {
                dates.add(Calendar.DAY_OF_YEAR, -timeAgo)
            } else if (dateWords[2].contains("dias")) {
                dates.add(Calendar.DAY_OF_YEAR, -timeAgo)
            }

            else if (dateWords[2].contains("semana")) {
                dates.add(Calendar.WEEK_OF_YEAR, -timeAgo)
            } else if (dateWords[2].contains("semanas")) {
                dates.add(Calendar.WEEK_OF_YEAR, -timeAgo)
            }

            else if (dateWords[2].contains("mes")) {
                dates.add(Calendar.MONTH, -timeAgo)
            } else if (dateWords[2].contains("meses")) {
                dates.add(Calendar.MONTH, -timeAgo)
            }

            else if (dateWords[2].contains("año")) {
                dates.add(Calendar.YEAR, -timeAgo)
            } else if (dateWords[2].contains("años")) {
                dates.add(Calendar.YEAR, -timeAgo)
            }
            return dates.timeInMillis
        }
        return 0L
    }

    override fun pageListRequest(chapter: SChapter): Request {
        //= GET(baseUrl + chapter.url, headers)
        val response = Jsoup.connect(baseUrl + chapter.url).get()
        val newUrl = response.select("a.cap-option").first().attr("href")

        return GET(baseUrl + newUrl, headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("img.cap-images").forEach {
            add(Page(size, "", baseUrl + it.attr("src")))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("imageUrlParse not implemented")
    }

    private class Types : Filter.Select<String>("Estilo",arrayOf("Todos", "Manga Japonés", "Manhwa Coreano", "Manhua Chino"))
    private class Status : Filter.Select<String>("Estado",arrayOf("Todos", "Finalizado", "En Curso"))
    private class Demography : Filter.Select<String>("Demografia",arrayOf("Todos", "Shonen", "Seinen", "Shojo", "Josei", "Kodomo", "Yuri", "Yaoi"))
    private class Genres : Filter.Select<String>("Generos",
        arrayOf(
            "Todos",
            "Acción",
            "Artes Marciales",
            "Aventura",
            "Ciencia Ficción",
            "Comedia",
            "Deporte",
            "Doujinshi",
            "Drama",
            "Ecchi",
            "Escolar",
            "Fantasía",
            "Gender Bender",
            "Gore",
            "Harem",
            "Histórico",
            "Horror",
            "Lolicon",
            "Magia",
            "Mecha",
            "Misterio",
            "Musical",
            "One-Shot",
            "Parodia",
            "Policíaca",
            "Psicológica",
            "Romance",
            "Shojo Ai",
            "Shonen Ai",
            "Shota",
            "Slice of Life",
            "Smut",
            "Sobrenatural",
            "Superpoderes",
            "Tragedia"
        )
    )

    override fun getFilterList() = FilterList(
        Types(),
        Status(),
        Demography(),
        Genres()
    )
}
