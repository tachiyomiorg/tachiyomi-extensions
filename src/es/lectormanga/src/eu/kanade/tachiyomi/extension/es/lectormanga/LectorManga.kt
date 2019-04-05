package eu.kanade.tachiyomi.extension.es.lectormanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Note: this source is similar to TuMangaOnline.
 */
class LectorManga : ParsedHttpSource() {

    override val name = "LectorManga"

    override val baseUrl = "https://lectormanga.com/"

    override val lang = "es"

    // TODO: the latest page returns a 500 right now
    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()!!

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
                .add("Referer", baseUrl)
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/60")
    }

    private fun getBuilder(url: String): String {
        val req = Request.Builder()
                .headers(headersBuilder()
                        .add("Cache-mode", "no-cache")
                        .build())
                .url(url)
                .build()

        return client.newCall(req)
                .execute()
                .request()
                .url()
                .toString()
    }

    override fun popularMangaSelector() = ".col-6 .card"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/library?order_item=likes_count&order_dir=desc&type=&filter_by=title&page=$page", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/library?order_item=creation&order_dir=desc&type=&filter_by=title&page=$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        genre = document.select("a.py-2").joinToString(", ") {
            it.text()
        }

        description = document.select(".col-12.mt-2")?.text()
        status = parseStatus(document.select(".status-publishing")?.text().orEmpty())
        thumbnail_url = document.select(".text-center img.img-fluid").attr("src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Publicándose") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun popularMangaNextPageSelector() = ".pagination .page-item:not(.disabled) a[rel='next']"

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
        if (document.select("#chapters").isEmpty()) {
            return document.select(oneShotChapterListSelector()).map { oneShotChapterFromElement(it) }
        }

        // Regular list of chapters
        val chapterNames = document.select("#chapters h4.text-truncate")
        val chapterInfos = document.select("#chapters .chapter-list")
        return (0 until chapterNames.size).map { regularChapterFromElement(
                chapterNames[it].text(),
                chapterInfos[it]) }
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

    private fun regularChapterFromElement(chapterName: String, info: Element) = SChapter.create().apply {
        setUrlWithoutDomain(info.select("div.row > .text-right > a").attr("href"))
        name = chapterName
        scanlator = info.select("div.col-md-6.text-truncate")?.text()
        date_upload = info.select("span.badge.badge-primary.p-2").first()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date).time

    override fun pageListRequest(chapter: SChapter): Request {
        val url = getBuilder(baseUrl + chapter.url)

        // Get /cascade instead of /paginate to get all pages at once
        return GET(url.substringBeforeLast("/") + "/cascade", headers)
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div#viewer-container > div.viewer-image-container > img.viewer-image")?.forEach {
            add(Page(size, "", it.attr("src")))
        }
    }

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
            Pair("DESC", "desc"),
            Pair("ASC", "asc")
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

    // Array.from(document.querySelectorAll('#advancedSearch .custom-checkbox')).map(a => `Genre("${a.querySelector('label').innerText}", "${a.querySelector('input').value}")`).join(',\n')
    // on https://lectormanga.com/library
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
