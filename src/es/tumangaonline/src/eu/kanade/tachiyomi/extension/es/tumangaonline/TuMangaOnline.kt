package eu.kanade.tachiyomi.extension.es.tumangaonline

import okhttp3.*
import java.util.*
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor

class TuMangaOnline : ParsedHttpSource() {

    override val name = "TuMangaOnline"

    override val baseUrl = "https://tmofans.com"

    override val lang = "es"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor).connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/60")
            .add("Referer", "$baseUrl/")
            .add("Cache-mode", "no-cache")
    }

    private fun getBuilder(url: String): String {
       val req = Request.Builder()
           .headers(headersBuilder().add("Referer", "$baseUrl/library/manga/").build())
           .url(url)
           .build()

       return client.newCall(req)
           .execute()
           .request()
           .url()
           .toString()
   }

    override fun popularMangaSelector() = "div.element"

    override fun latestUpdatesSelector() = "div.upload-file-row"
    //override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/library?order_item=likes_count&order_dir=desc&type=&filter_by=title&page=$page", headers)

    //override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/library?order_item=creation&order_dir=desc&type=&filter_by=title&page=$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page&uploads_mode=thumbnail#latest_uploads", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("div.element > a").let {
            setUrlWithoutDomain(it.attr("href").substringAfter(" "))
            title = it.select("h4.text-truncate").text()
            thumbnail_url = it.select("style").toString().substringAfter("('").substringBeforeLast("')")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("div.upload-file-row > a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("div.thumbnail-title > h4.text-truncate").text()
            thumbnail_url = it.select("div.thumbnail > style").toString().substringAfter("url('").substringBefore("');")
        }
    }

    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        document.select("h5.card-title").let {
            author = it?.first()?.attr("title")?.substringAfter(", ")
            artist = it?.last()?.attr("title")?.substringAfter(", ")
        }

        genre = document.select("a.py-2").joinToString(", ") {
            it.text()
        }

        description = document.select("p.element-description")?.text()
        status = parseStatus(document.select("span.book-status")?.text().orEmpty())
        thumbnail_url = document.select(".book-thumbnail").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Publicándose") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun popularMangaNextPageSelector() = "a.page-link"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/library")!!.newBuilder()

        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is Types -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is Demography -> {
                    url.addQueryParameter("demography", filter.toUriPart())
                }
                is FilterBy -> {
                    url.addQueryParameter("filter_by", filter.toUriPart())
                }
                is OrderBy -> {
                    url.addQueryParameter("order_item", filter.toUriPart())
                }
                is OrderDir -> {
                    url.addQueryParameter("order_dir", filter.toUriPart())
                }
                is WebcomicFilter -> {
                    url.addQueryParameter("webcomic", when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "true"
                        Filter.TriState.STATE_EXCLUDE -> "false"
                        else -> ""
                    })
                }
                is FourKomaFilter -> {
                    url.addQueryParameter("yonkoma", when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "true"
                        Filter.TriState.STATE_EXCLUDE -> "false"
                        else -> ""
                    })
                }
                is AmateurFilter -> {
                    url.addQueryParameter("amateur", when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "true"
                        Filter.TriState.STATE_EXCLUDE -> "false"
                        else -> ""
                    })
                }
                is EroticFilter -> {
                    url.addQueryParameter("erotic", when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "true"
                        Filter.TriState.STATE_EXCLUDE -> "false"
                        else -> ""
                    })
                }
                is GenreList -> {
                    filter.state
                        .filter { genre -> genre.state }
                        .forEach { genre -> url.addQueryParameter("genders[]", genre.id) }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // One-shot
        if (document.select("div.chapters").isEmpty()) {
            return document.select(oneShotChapterListSelector()).map { oneShotChapterFromElement(it) }
        }

        // Regular list of chapters
        return document.select(regularChapterListSelector()).map { regularChapterFromElement(it) }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    private fun oneShotChapterListSelector() = "div.chapter-list-element > ul.list-group li.list-group-item"

    private fun oneShotChapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("div.row > .text-right > a").attr("href"))
        name = "One Shot"
        scanlator = element.select("div.col-md-6.text-truncate")?.text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun regularChapterListSelector() = "div.chapters > ul.list-group li.p-0.list-group-item"

    private fun regularChapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("div.row > .text-right > a").attr("href"))
        name = element.select("div.col-10.text-truncate").text()
        scanlator = element.select("div.col-md-6.text-truncate")?.text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date).time

    override fun pageListRequest(chapter: SChapter): Request {
        val url = getBuilder(baseUrl + chapter.url).substringBeforeLast("/") + "/cascade"

        // Get /cascade instead of /paginate to get all pages at once
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val body = response.asJsoup()

        body.select("div#viewer-container > div.viewer-image-container > img.viewer-image")?.forEach {
            add(Page(size, "", it.attr("src")))
        }
    }

    override fun pageListParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun imageUrlRequest(page: Page) = GET(page.url, headers)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    private class Types : UriPartFilter("Tipo", arrayOf(
        Pair("Ver todo", ""),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Manhwa", "manhwa"),
        Pair("Novela", "novel"),
        Pair("One shot", "one_shot"),
        Pair("Doujinshi", "doujinshi"),
        Pair("Oel", "oel")
    ))

    private class Demography : UriPartFilter("Demografía", arrayOf(
        Pair("Ver todo", ""),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Josei", "josei"),
        Pair("Kodomo", "kodomo")
    ))

    private class FilterBy : UriPartFilter("Ordenar por", arrayOf(
        Pair("Título", "title"),
        Pair("Autor", "author"),
        Pair("Compañia", "company")
    ))

    private class OrderBy : UriPartFilter("Ordenar por", arrayOf(
        Pair("Me gusta", "likes_count"),
        Pair("Alfabético", "alphabetically"),
        Pair("Puntuación", "score"),
        Pair("Creación", "creation"),
        Pair("Fecha estreno", "release_date")
    ))

    private class OrderDir : UriPartFilter("Ordenar por", arrayOf(
        Pair("ASC", "asc"),
        Pair("DESC", "desc")
    ))

    private class WebcomicFilter : Filter.TriState("Webcomic")
    private class FourKomaFilter : Filter.TriState("Yonkoma")
    private class AmateurFilter : Filter.TriState("Amateur")
    private class EroticFilter : Filter.TriState("Erótico")

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Géneros", genres)

    override fun getFilterList() = FilterList(
        Types(),
        Demography(),
        Filter.Separator(),
        FilterBy(),
        OrderBy(),
        OrderDir(),
        Filter.Separator(),
        WebcomicFilter(),
        FourKomaFilter(),
        AmateurFilter(),
        EroticFilter(),
        GenreList(getGenreList())
    )

    // Array.from(document.querySelectorAll('#books-genders .col-auto .custom-control')).map(a => `Genre("${a.querySelector('label').innerText}", "${a.querySelector('input').value}")`).join(',\n')
    // on https://tumangaonline.me/library
    private fun getGenreList() = listOf(
        Genre("Acción", "1"),
        Genre("Aventura", "2"),
        Genre("Comedia", "3"),
        Genre("Drama", "4"),
        Genre("Recuentos de la vida", "5"),
        Genre("Ecchi", "6"),
        Genre("Fantasia", "7"),
        Genre("Magia", "8"),
        Genre("Sobrenatural", "9"),
        Genre("Horror", "10"),
        Genre("Misterio", "11"),
        Genre("Psicológico", "12"),
        Genre("Romance", "13"),
        Genre("Ciencia Ficción", "14"),
        Genre("Thriller", "15"),
        Genre("Deporte", "16"),
        Genre("Girls Love", "17"),
        Genre("Boys Love", "18"),
        Genre("Harem", "19"),
        Genre("Mecha", "20"),
        Genre("Supervivencia", "21"),
        Genre("Reencarnación", "22"),
        Genre("Gore", "23"),
        Genre("Apocalíptico", "24"),
        Genre("Tragedia", "25"),
        Genre("Vida Escolar", "26"),
        Genre("Historia", "27"),
        Genre("Militar", "28"),
        Genre("Policiaco", "29"),
        Genre("Crimen", "30"),
        Genre("Superpoderes", "31"),
        Genre("Vampiros", "32"),
        Genre("Artes Marciales", "33"),
        Genre("Samurái", "34"),
        Genre("Género Bender", "35"),
        Genre("Realidad Virtual", "36"),
        Genre("Ciberpunk", "37"),
        Genre("Musica", "38"),
        Genre("Parodia", "39"),
        Genre("Animación", "40"),
        Genre("Demonios", "41"),
        Genre("Familia", "42"),
        Genre("Extranjero", "43"),
        Genre("Niños", "44"),
        Genre("Realidad", "45"),
        Genre("Telenovela", "46"),
        Genre("Guerra", "47"),
        Genre("Oeste", "48")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
