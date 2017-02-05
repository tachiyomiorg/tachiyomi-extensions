package eu.kanade.tachiyomi.extension.it.perveden

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Perveden : ParsedHttpSource() {

    override val name = "Perveden"

    override val baseUrl = "http://www.perveden.com"

    override val lang = "it"

    override val supportsLatest = true

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/it/it-directory/?order=3&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/it/it-directory/?order=1&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/it/it-directory/").newBuilder().addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Statuses -> {
                    val id = filter.values[filter.state].id
                    if (id != -1) url.addQueryParameter("status", id.toString())
                }
                is Types -> {
                    val id = filter.values[filter.state].id
                    if (id != -1) url.addQueryParameter("type", id.toString())
                }
                is GenreList -> filter.state
                        .filter { !it.isIgnored() }
                        .forEach { genre -> url.addQueryParameter(if (genre.isIncluded()) "categoriesInc" else "categoriesExcl", genre.id) }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is OrderBy -> filter.state?.let {
                    val sortId = it.index
                    url.addQueryParameter("order", if (it.ascending) "-$sortId" else "$sortId")
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table#mangaList > tbody > tr:has(td:gt(1))"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("td > a").first().let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infos = document.select("div.rightbox")

        author = infos.select("a[href^=/it/it-directory/?author]").first().text()
        artist = infos.select("a[href^=/it/it-directory/?artist]").first().text()
        genre = infos.select("a[href^=/it/it-directory/?categoriesInc]").map { it.text() }.joinToString()
        description = document.select("h2#mangaDescription").text()
        status = parseStatus(infos.select("h4:containsOwn(Stato)").first().nextSibling().toString())
        thumbnail_url = "http:${infos.select("div.mangaImage2 > img").first().attr("src")}"
    }

    private fun parseStatus(status: String) = when {
        status.contains("In Corso", true) -> SManga.ONGOING
        status.contains("Completato", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#leftContent > table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.select("a[href^=/it/it-manga/]").first()

        setUrlWithoutDomain(a.attr("href"))
        name = a.select("b").first().text()
        date_upload = element.select("td.chapterDate").first().text().let { parseChapterDate(it.trim()) }
    }

    private fun parseChapterDate(date: String): Long = try {
        SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).parse(date).time
    } catch (e: ParseException) {
        0L
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("option[value^=/it/it-manga/]").forEach {
            add(Page(size, "$baseUrl${it.attr("value")}"))
        }
    }

    override fun imageUrlParse(document: Document): String = "http:${document.select("a#nextA.next > img").first().attr("src")}"

    private class NamedId(val name: String, val id: Int) {
        override fun toString(): String = name
    }

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Statuses(statuses: Array<NamedId>) : Filter.Select<NamedId>("Stato", statuses)
    private class Types(types: Array<NamedId>) : Filter.Select<NamedId>("Tipo", types)
    private class OrderBy : Filter.Sort("Ordina per", arrayOf("Titolo manga", "Visite", "Capitoli", "Ultimo capitolo"),
            Selection(1, false))

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Generi", genres)

    override fun getFilterList() = FilterList(
            TextField("Autore", "author"),
            TextField("Artista", "artist"),
            Types(types),
            Statuses(statuses),
            OrderBy(),
            GenreList(genres)
    )

    private val types = arrayOf(
            NamedId("Tutti", -1),
            NamedId("Japanese Manga", 0),
            NamedId("Korean Manhwa", 1),
            NamedId("Chinese Manhua", 2),
            NamedId("Comic", 3),
            NamedId("Doujinshi", 4)
    )
    private val statuses = arrayOf(
            NamedId("Tutti", -1),
            NamedId("In corso", 1),
            NamedId("Completato", 2),
            NamedId("Sospeso", 0)
    )
    private val genres = listOf(
            Genre("Avventura", "4e70ea8cc092255ef70073d3"),
            Genre("Azione", "4e70ea8cc092255ef70073c3"),
            Genre("Bara", "4e70ea90c092255ef70074b7"),
            Genre("Commedia", "4e70ea8cc092255ef70073d0"),
            Genre("Demenziale", "4e70ea8fc092255ef7007475"),
            Genre("Dounshinji", "4e70ea93c092255ef70074e4"),
            Genre("Drama", "4e70ea8cc092255ef70073f9"),
            Genre("Ecchi", "4e70ea8cc092255ef70073cd"),
            Genre("Fantasy", "4e70ea8cc092255ef70073c4"),
            Genre("Harem", "4e70ea8cc092255ef70073d1"),
            Genre("Hentai", "4e70ea90c092255ef700749a"),
            Genre("Horror", "4e70ea8cc092255ef70073ce"),
            Genre("Josei", "4e70ea90c092255ef70074bd"),
            Genre("Magico", "4e70ea93c092255ef700751b"),
            Genre("Mecha", "4e70ea8cc092255ef70073ef"),
            Genre("Misteri", "4e70ea8dc092255ef700740a"),
            Genre("Musica", "4e70ea8fc092255ef7007456"),
            Genre("Psicologico", "4e70ea8ec092255ef7007439"),
            Genre("Raccolta", "4e70ea90c092255ef70074ae"),
            Genre("Romantico", "4e70ea8cc092255ef70073c5"),
            Genre("Sci-Fi", "4e70ea8cc092255ef70073e4"),
            Genre("Scolastico", "4e70ea8cc092255ef70073e5"),
            Genre("Seinen", "4e70ea8cc092255ef70073ea"),
            Genre("Sentimentale", "4e70ea8dc092255ef7007432"),
            Genre("Shota", "4e70ea90c092255ef70074b8"),
            Genre("Shoujo", "4e70ea8dc092255ef7007421"),
            Genre("Shounen", "4e70ea8cc092255ef70073c6"),
            Genre("Sovrannaturale", "4e70ea8cc092255ef70073c7"),
            Genre("Splatter", "4e70ea99c092255ef70075a3"),
            Genre("Sportivo", "4e70ea8dc092255ef7007426"),
            Genre("Storico", "4e70ea8cc092255ef70073f4"),
            Genre("Vita Quotidiana", "4e70ea8ec092255ef700743f"),
            Genre("Yaoi", "4e70ea8cc092255ef70073de"),
            Genre("Yuri", "4e70ea9ac092255ef70075d1")
    )
}