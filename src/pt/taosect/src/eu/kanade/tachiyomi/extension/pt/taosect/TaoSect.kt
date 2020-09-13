package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.UnsupportedOperationException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class TaoSect : ParsedHttpSource() {

    override val name = "Tao Sect"

    override val baseUrl = "https://taosect.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/situacao/ativos", headers)
    }

    override fun popularMangaSelector(): String = "div.post-list article.post-projeto"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val sibling = element.nextElementSibling()!!

        title = sibling.select("h3.titulo-popover").text()!!
        thumbnail_url = element.select("div.post-projeto-background")!!
            .attr("style")
            .substringAfter("url(")
            .substringBefore(");")
        setUrlWithoutDomain(element.select("a[title]").first()!!.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/pesquisar-leitor")!!.newBuilder()
            .addQueryParameter("leitor_titulo_projeto", query)

        filters.forEach { filter ->
            when (filter) {
                is CountryFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("leitor_pais_projeto[]", it.id) }
                }
                is StatusFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("leitor_status_projeto[]", it.id) }
                }
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.isIncluded()) {
                            url.addQueryParameter("leitor_tem_genero_projeto[]", genre.id)
                        } else if (genre.isExcluded()) {
                            url.addQueryParameter("leitor_n_tem_genero_projeto[]", genre.id)
                        }
                    }
                }
                is SortFilter -> {
                    val sort = when {
                        filter.state == null -> "a_z"
                        filter.state!!.ascending -> SORT_LIST[filter.state!!.index].first
                        else -> SORT_LIST[filter.state!!.index].second
                    }

                    url.addQueryParameter("leitor_ordem_projeto", sort)
                }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "article.manga_item"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h2.titulo_manga_item a")!!.text()
        thumbnail_url = element.select("div.container_imagem")!!
            .attr("style")
            .substringAfter("url(")
            .substringBefore(");")
        setUrlWithoutDomain(element.select("h2.titulo_manga_item a")!!.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val header = document.select("div.cabelho-projeto").first()!!

        title = header.select("h1.titulo-projeto")!!.text()
        author = header.select("table.tabela-projeto tr:eq(1) td:eq(1)")!!.text()
        artist = header.select("table.tabela-projeto tr:eq(0) td:eq(1)")!!.text()
        genre = header.select("table.tabela-projeto tr:eq(10) a").joinToString { it.text() }
        status = header.select("table.tabela-projeto tr:eq(4) td:eq(1)")!!.text().toStatus()
        description = header.select("table.tabela-projeto tr:eq(9) p")!!.text()
        thumbnail_url = header.select("div.imagens-projeto img[alt]").first()!!.attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "table.tabela-volumes tr:not(:first-child)"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("td[align='left'] a")!!.text()
        scanlator = this@TaoSect.name
        date_upload = DATE_FORMATTER.tryParseTime(element.select("td[align='right']")!!.text())
        setUrlWithoutDomain(element.select("td[align='left'] a")!!.attr("href"))
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val projectUrl = "$baseUrl/" + chapter.url
            .substringAfter("online/")
            .substringBefore("/")

        val newHeaders = headersBuilder()
            .set("Referer", projectUrl)
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(var paginas)").first()!!
            .data()
            .substringAfter("var paginas = [")
            .substringBefore("];")
            .split(",")
            .mapIndexed { i, url ->
                Page(i, document.location(), url.replace("\"", ""))
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList = FilterList(
        CountryFilter(getCountryList()),
        StatusFilter(getStatusList()),
        GenreFilter(getGenreList()),
        SortFilter()
    )

    private class Tag(val id: String, name: String) : Filter.CheckBox(name)

    private class Genre(val id: String, name: String) : Filter.TriState(name)

    private class CountryFilter(countries: List<Tag>) : Filter.Group<Tag>("País", countries)

    private class StatusFilter(status: List<Tag>) : Filter.Group<Tag>("Status", status)

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres)

    private class SortFilter : Filter.Sort(
        "Ordem",
        SORT_LIST.map { it.third }.toTypedArray(),
        Selection(0, true)
    )

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    private fun SimpleDateFormat.tryParseTime(date: String): Long {
        return try {
            parse(date)!!.time
        } catch (e: ParseException) {
            0L
        }
    }

    private fun String.toStatus() = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Finalizado") || contains("Oneshots") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun getCountryList(): List<Tag> = listOf(
        Tag("59", "China"),
        Tag("60", "Coréia do Sul"),
        Tag("13", "Japão")
    )

    private fun getStatusList(): List<Tag> = listOf(
        Tag("3", "Ativo"),
        Tag("5", "Cancelado"),
        Tag("4", "Finalizado"),
        Tag("6", "One-shot")
    )

    // [...document.querySelectorAll("#leitor_tem_genero_projeto option")]
    //     .map(el => `Genre("${el.getAttribute("value")}", "${el.innerText}")`).join(',\n')
    private fun getGenreList(): List<Genre> = listOf(
        Genre("31", "4Koma"),
        Genre("24", "Ação"),
        Genre("84", "Adulto"),
        Genre("21", "Artes Marciais"),
        Genre("25", "Aventura"),
        Genre("26", "Comédia"),
        Genre("66", "Culinária"),
        Genre("78", "Doujinshi"),
        Genre("22", "Drama"),
        Genre("12", "Ecchi"),
        Genre("30", "Escolar"),
        Genre("76", "Esporte"),
        Genre("23", "Fantasia"),
        Genre("29", "Harém"),
        Genre("75", "Histórico"),
        Genre("83", "Horror"),
        Genre("18", "Isekai"),
        Genre("20", "Light Novel"),
        Genre("61", "Manhua"),
        Genre("56", "Psicológico"),
        Genre("7", "Romance"),
        Genre("27", "Sci-fi"),
        Genre("28", "Seinen"),
        Genre("55", "Shoujo"),
        Genre("54", "Shounen"),
        Genre("19", "Slice of life"),
        Genre("17", "Sobrenatural"),
        Genre("57", "Tragédia"),
        Genre("62", "Webtoon")
    )

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36"

        private val DATE_FORMATTER by lazy { SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH) }

        private val SORT_LIST = listOf(
            Triple("a_z", "z_a", "Nome"),
            Triple("dt_asc", "date-desc", "Data")
        )
    }
}
