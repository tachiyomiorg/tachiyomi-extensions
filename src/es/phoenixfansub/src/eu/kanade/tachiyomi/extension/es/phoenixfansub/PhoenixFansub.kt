package eu.kanade.tachiyomi.extension.es.phoenixfansub

import android.net.Uri
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

open class PhoenixFansub : ParsedHttpSource() {

    override val name = "Phoenix Fansub"

    override val baseUrl = "https://phoenixfansub.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int) = GET(
        "$baseUrl/proyectos/page/$page",
        headers
    )

    override fun popularMangaNextPageSelector() = ".next"

    override fun popularMangaSelector() = ".listupd .bs"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = element.select("img").attr("src")
        title = element.select(".bigor .tt").text().trim()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesNextPageSelector(): String? = ".listupd:eq(1) .hpage a.r"

    override fun latestUpdatesSelector() = ".listupd:eq(1) .uta"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("src")
        element.select("div a").apply {
            title = this.text().trim()
            setUrlWithoutDomain(this.attr("href"))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return GET("$baseUrl/page/$page/?s=$query")
        } else {
            val uri = Uri.parse("$baseUrl/manga/?page=$page").buildUpon()

            for (filter in filters) {
                when (filter) {
                    is StatusFilter -> uri.appendQueryParameter(
                        "status",
                        statusArray[filter.state].second
                    )
                    is SortBy -> uri.appendQueryParameter(
                        "order",
                        sortables[filter.state].second
                    )
                    is TypeFilter -> uri.appendQueryParameter(
                        "type",
                        typedArray[filter.state].second
                    )
                    is GenreFilter -> uri.appendQueryParameter(
                        "genre[]",
                        genresArray[filter.state].second
                    )
                }
            }
            return GET(uri.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.isSuccessful) throw Exception("Búsqueda fallida ${response.code}")
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select(".thumb img").attr("src")
        manga.description = document.select(".entry-content").text().trim()
        manga.author = document.select(".fmed:contains(Author) span").text().trim()
        manga.artist = document.select(".fmed:contains(Artist) span").text().trim()
        manga.genre = document.select(".mgen a").joinToString(", ") { it.text() }
        manga.status = when (document.select(".imptdt i").text().trim()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun chapterListSelector(): String = "#chapterlist li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select(".chapternum").text().trim()
        setUrlWithoutDomain(element.select("a").attr("href"))
        chapter_number = element.attr("data-num").toFloat()
        date_upload = parseDate(element.select(".chapterdate").text())
    }

    private fun parseDate(date: String): Long {
        val spanishLocale = Locale("es", "ES")
        return SimpleDateFormat("MMMM d, yyyy", spanishLocale).parse(date)?.time ?: 0
    }

    override fun pageListParse(document: Document): List<Page> {
        var images = document.select("script")[25].html().split(".run(")[1]
        images = images.subSequence(0, images.length - 2) as String
        val json = JsonParser.parseString(images)
        val imageArray = json["sources"][0]["images"]
        return (imageArray as Iterable<*>).mapIndexed { i, img ->
            Page(i, "", img.toString().subSequence(1, img.toString().length - 1) as String)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: Se ignoran si se usa el buscador"),
        Filter.Separator(),
        SortBy("Ordenar por", sortables),
        StatusFilter("Estado", statusArray),
        TypeFilter("Tipo", typedArray),
        GenreFilter("Géneros", genresArray)
    )

    private class StatusFilter(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private class TypeFilter(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private class GenreFilter(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private class SortBy(name: String, values: Array<Pair<String, String>>) :
        Filter.Select<String>(name, values.map { it.first }.toTypedArray())

    private val statusArray = arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed"),
        Pair("Hiatus", "hiatus")
    )

    private val typedArray = arrayOf(
        Pair("All", ""),
        Pair("Manga", "manga"),
        Pair("Manhwa", "manhwa"),
        Pair("Manhua", "manhua"),
        Pair("Comic", "comic")
    )

    private val sortables = arrayOf(
        Pair("Default", ""),
        Pair("A-Z", "title"),
        Pair("Z-A", "titlereverse"),
        Pair("Update", "update"),
        Pair("Added", "latest"),
        Pair("Popular", "popular")
    )

    private val genresArray = arrayOf(
        Pair("Todos", ""),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Mystery", "mystery"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Supernatural", "supernatural")
    )
}
