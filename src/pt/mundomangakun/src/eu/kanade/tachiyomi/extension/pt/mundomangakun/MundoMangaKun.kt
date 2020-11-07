package eu.kanade.tachiyomi.extension.pt.mundomangakun

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
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

class MundoMangaKun : ParsedHttpSource() {

    override val name = "Mundo Mangá-Kun"

    override val baseUrl = "https://mundomangakun.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        val refererPath = if (page <= 2) "" else "/leitor-online/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + refererPath)
            .build()

        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/leitor-online$pageStr", newHeaders)
    }

    override fun popularMangaSelector(): String = "div.leitor_online_container article.manga_item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h2.titulo_manga_item").text()
        thumbnail_url = element.select("div.container_imagem").attr("style")
            .substringAfter("url(")
            .substringBefore(");")
        setUrlWithoutDomain(element.select("h2.titulo_manga_item a").attr("href"))
    }

    override fun popularMangaNextPageSelector() = "div.paginacao a.next.page-numbers"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val refererPage = if (page <= 2) "" else "page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/leitor-online/$refererPage")
            .build()

        val pagePath = if (page != 1) "page/$page/" else ""
        val url = HttpUrl.parse("$baseUrl/leitor-online/$pagePath")!!.newBuilder()
            .addQueryParameter("leitor_titulo_projeto", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    url.addQueryParameter("leitor_genero_projeto", filter.selected.value)
                }
                is StatusFilter -> {
                    url.addQueryParameter("leitor_status_projeto", filter.selected.value)
                }
                is SortFilter -> {
                    val order = if (filter.state!!.ascending) "ASC" else "DESC"
                    url.addQueryParameter("leitor_ordem_projeto", order)
                }
            }
        }

        return GET(url.toString(), newHeaders)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.main_container_projeto div.row").first()
        val colInfo = infoElement.select("div.col-sm-7").first()
        val colImg = infoElement.select("div.col-sm-5").first()
        val tableInfo = colInfo.select("table.tabela_info_projeto").first()

        title = colInfo.select("h1.titulo_projeto").text()
        author = tableInfo.select("td:contains(Roteiro) + td").text()
        artist = tableInfo.select("td:contains(Arte) + td").text()
        genre = colImg.select("div.generos a.link_genero").joinToString { it.text() }
        status = tableInfo.select("td:contains(Status no Scan) + td").text().toStatus()
        description = colInfo.select("h2:contains(Sinopse) + div.conteudo_projeto").text()
        thumbnail_url = infoElement.select("div.imagens_projeto_container img").first().attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "div.capitulos_leitor_online a.link_capitulo"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        scanlator = this@MundoMangaKun.name

        val link = element.attr("onclick")
            .substringAfter("this,")
            .substringBeforeLast(")")
            .let { JSON_PARSER.parse(it) }
            .array
            .first { it.obj["tipo"].string == "LEITOR" }

        setUrlWithoutDomain(link.obj["link"].string)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(var paginas)").first().data()
            .substringAfter("var paginas=")
            .substringBefore(";var")
            .let { JSON_PARSER.parse(it) }
            .array
            .mapIndexed { i, page -> Page(i, document.location(), page.string) }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(getGenreList()),
        StatusFilter(getStatusList()),
        SortFilter()
    )

    data class Tag(val text: String, val value: String) {
        override fun toString(): String = text
    }

    open class TagFilter(name: String, tags: List<Tag>) : Filter.Select<Tag>(name, tags.toTypedArray()) {
        val selected: Tag
            get() = values[state]
    }

    class GenreFilter(genres: List<Tag>) : TagFilter("Gênero", genres)
    class StatusFilter(status: List<Tag>) : TagFilter("Status", status)
    class SortFilter : Filter.Sort("Ordem", arrayOf("Alfabeticamente"), Selection(0, true))

    // [...document.querySelectorAll('#leitor_genero_projeto option')]
    //     .map(x => `Tag("${x.innerText}", "${x.value}")`)
    //     .join(',\n')
    private fun getGenreList() = listOf(
        Tag("Selecione…", ""),
        Tag("Ação", "59"),
        Tag("Adulto", "63"),
        Tag("Artes Marciais", "77"),
        Tag("Aventura", "65"),
        Tag("Comédia", "30"),
        Tag("Drama", "17"),
        Tag("Ecchi", "74"),
        Tag("Escolar", "64"),
        Tag("Esportes", "87"),
        Tag("Fantasia", "31"),
        Tag("Harem", "82"),
        Tag("hentai", "525"),
        Tag("Histórico", "95"),
        Tag("Josei", "553"),
        Tag("Mistério", "19"),
        Tag("Oneshot", "527"),
        Tag("Psicológico", "20"),
        Tag("Romance", "75"),
        Tag("Sci-fi", "66"),
        Tag("Seinen", "61"),
        Tag("Serial Killer", "93"),
        Tag("Shoujo", "568"),
        Tag("Shoujo Ai", "92"),
        Tag("Shounen", "67"),
        Tag("Slice Of Life", "94"),
        Tag("Sobrenatural", "76"),
        Tag("Sobrevivência", "90"),
        Tag("Super Poderes", "425"),
        Tag("Supernatual", "60"),
        Tag("Suspense", "520"),
        Tag("Terror", "18"),
        Tag("Tragédia", "21"),
        Tag("Yuri", "526")
    )

    // [...document.querySelectorAll('#leitor_status_projeto option')]
    //     .map(x => `Tag("${x.innerText}", "${x.value}")`)
    //     .join(',\n')
    private fun getStatusList() = listOf(
        Tag("Selecione…", ""),
        Tag("Cancelado", "6"),
        Tag("Em Andamento", "8"),
        Tag("Finalizado", "7"),
        Tag("One Shot", "4")
    )

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    private fun String.toStatus(): Int = when (this) {
        "Em Andamento" -> SManga.ONGOING
        "Finalizado", "One Shot" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.121 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }
    }
}
