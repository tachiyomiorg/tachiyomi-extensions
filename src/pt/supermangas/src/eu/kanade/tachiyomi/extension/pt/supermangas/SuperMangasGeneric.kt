package eu.kanade.tachiyomi.extension.pt.supermangas

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

typealias Content = Triple<String, String, String>

abstract class SuperMangasGeneric(
    override val name: String,
    override val baseUrl: String,
    private val listPath: String = "lista"
) : ParsedHttpSource() {

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)
        .add("Accept", ACCEPT_COMMON)
        .add("Accept-Language", ACCEPT_LANGUAGE)

    protected open val defaultFilter = mutableMapOf(
        "filter_display_view" to "lista",
        "filter_letter" to "0",
        "filter_order" to "more_access",
        "filter_type_content" to "100",
        "filter_genre_model" to "yes",
        "filter_status" to "0",
        "filter_size_start" to "0",
        "filter_size_final" to "0",
        "filter_date" to "0",
        "filter_date_ordem" to "0",
        "filter_censure" to "0",
        "filter_idade" to "",
        "filter_dub" to "0",
        "filter_viewed" to "0"
    )

    protected open val contentList: List<Content> = listOf()

    protected open val chapterListOrder: String = "desc"

    private fun genericPaginatedRequest(
        typeUrl: String,
        filterData: Map<String, String> = defaultFilter,
        filterGenreAdd: List<String> = emptyList(),
        filterGenreDel: List<String> = emptyList(),
        page: Int = 1
    ): Request {
        val filters = jsonObject(
            "filter_data" to filterData.toUrlQueryParams(),
            "filter_genre_add" to jsonArray(filterGenreAdd),
            "filter_genre_del" to jsonArray(filterGenreDel)
        )

        val form = FormBody.Builder()
            .add("type_url", typeUrl)
            .add("page", page.toString())
            .add("limit", "24")
            .add("type", "lista")
            .add("filters", filters.toString())
            .build()

        val newHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Type", form.contentType().toString())
            .add("Content-Length", form.contentLength().toString())
            .add("Host", "www." + baseUrl.substringAfter("//"))
            .set("Referer", "$baseUrl/$typeUrl")
            .build()

        return POST("$baseUrl/inc/paginator.inc.php", newHeaders, form)
    }

    private fun genericMangaFromElement(element: Element, imageAttr: String = "src"): SManga = SManga.create().apply {
        title = element.select("img").first().attr("alt")
        thumbnail_url = element.select("img").first().attr(imageAttr).changeSize()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaRequest(page: Int): Request = genericPaginatedRequest(listPath, page = page)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asJsonObject()

        if (result["codigo"].int == 0)
            return MangasPage(emptyList(), false)

        val document = Jsoup.parse(result["body"].array.joinToString("") { it.string })

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val requestBody = response.request().body() as FormBody
        val totalPage = result["total_page"].string.toInt()
        val page = requestBody.value("page").toInt()
        val hasNextPage = page < totalPage

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaSelector(): String = "article.box_view.list div.grid_box div.grid_image.grid_image_vertical a"

    override fun popularMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        val filterData = defaultFilter.toMutableMap()
            .apply { this["filter_order"] = "date-desc" }

        return genericPaginatedRequest(listPath, filterData, page = page)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = genericMangaFromElement(element, "data-src")

    override fun latestUpdatesNextPageSelector(): String? = null

    protected open fun searchMangaWithQueryRequest(query: String): Request {
        val searchUrl = HttpUrl.parse("$baseUrl/busca")!!.newBuilder()
            .addEncodedQueryParameter("parametro", query)
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return searchMangaWithQueryRequest(query)
        }

        val newFilterData = defaultFilter.toMutableMap()
        val filterGenreAdd = mutableListOf<String>()
        val filterGenreDel = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is ContentFilter -> {
                    newFilterData["filter_type_content"] = contentList[filter.state].first
                }
                is LetterFilter -> {
                    newFilterData["filter_letter"] =
                        if (filter.state < 2) filter.state.toString()
                        else LETTER_LIST[filter.state]
                }
                is StatusFilter -> {
                    newFilterData["filter_status"] = STATUS_LIST[filter.state].first
                }
                is CensureFilter -> {
                    newFilterData["filter_censure"] = CENSURE_LIST[filter.state].first
                }
                is SortFilter -> {
                    newFilterData["filter_order"] = when {
                        filter.state == null -> "0"
                        filter.state!!.ascending -> SORT_LIST[filter.state!!.index].first
                        else -> SORT_LIST[filter.state!!.index].second
                    }
                }
                is ExclusiveModeFilter -> {
                    newFilterData["filter_genre_model"] = if (filter.state) "yes" else "no"
                }
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.isExcluded()) {
                            filterGenreDel.add(genre.id)
                        } else if (genre.isIncluded()) {
                            filterGenreAdd.add(genre.id)
                        }
                    }
                }
            }
        }

        return genericPaginatedRequest(listPath, newFilterData, filterGenreAdd, filterGenreDel, page = page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request().url().toString().contains("busca"))
            return super.searchMangaParse(response)

        return popularMangaParse(response)
    }

    override fun searchMangaSelector() = "div.boxConteudo " + popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = genericMangaFromElement(element, "data-src")

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.boxAnime").first()!!
        val infoElementAbout = infoElement.select("ul.boxAnimeSobre")

        title = document.select("div.boxBarraInfo h1").first()!!.text()
        author = infoElementAbout.select("li:contains(Autor) span").first()!!.text()
        artist = infoElementAbout.select("li:contains(Art:) span").first()!!.text()
        genre = infoElementAbout.select("li.sizeFull span a").joinToString { it.text() }
        status = infoElementAbout.select("li:contains(Conteúdo)").first()!!.text().toStatus()
        description = document.select("p#sinopse").first()!!.text()
        thumbnail_url = infoElement.select("span.boxAnimeImg img").first()!!.attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val lastPage = document.select("select.pageSelect option").last()!!
            .attr("value").toInt()
        val idCategory = document.select("div#listaDeConteudo").first()!!
            .attr("data-id-cat").toInt()
        val mangaUrl = response.request().url().toString()

        val chapters = mutableListOf<SChapter>()

        for (page in 1..lastPage) {
            val chapterListRequest = chapterListPaginatedRequest(idCategory, page, lastPage, mangaUrl)
            val result = client.newCall(chapterListRequest).execute()
            val apiResponse = result.asJsonObject()

            if (apiResponse["codigo"].int == 0) break

            val htmlBody = apiResponse["body"].array.joinToString("") { it.string }
            chapters += Jsoup.parse(htmlBody)
                .select(chapterListSelector())
                .map { chapterFromElement(it) }
        }

        return chapters
    }

    protected open fun chapterListPaginatedBody(idCategory: Int, page: Int, totalPage: Int): FormBody.Builder {
        return FormBody.Builder()
            .add("id_cat", idCategory.toString())
            .add("page", page.toString())
            .add("limit", "50")
            .add("total_page", totalPage.toString())
            .add("order_video", chapterListOrder)
            .add("type", "book")
    }

    protected fun chapterListPaginatedRequest(idCategory: Int, page: Int, totalPage: Int, mangaUrl: String): Request {
        val form = chapterListPaginatedBody(idCategory, page, totalPage).build()

        val newHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Type", form.contentType().toString())
            .add("Content-Length", form.contentLength().toString())
            .add("Host", "www." + baseUrl.substringAfter("//"))
            .set("Referer", mangaUrl)
            .set("Accept", ACCEPT_JSON)
            .build()

        return POST("$baseUrl/inc/paginatorVideo.inc.php", newHeaders, form)
    }

    override fun chapterListSelector() = "div.boxTop10 div.top10Link"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").first()!!.text()
        chapter_number = element.select("span[style]").first()!!.text().toFloatOrNull() ?: -1f
        setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.capituloViewBox img.lazy")
            .mapIndexed { i, element ->
                Page(i, document.location(), element.absUrl("data-src"))
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    protected class Tag(val id: String, name: String) : Filter.TriState(name)

    protected class ContentFilter(contents: List<Content>) : Filter.Select<String>(
        "Tipo de Conteúdo",
        contents.map { it.third }.toTypedArray()
    )

    protected class LetterFilter : Filter.Select<String>("Letra inicial", LETTER_LIST)

    protected class StatusFilter : Filter.Select<String>(
        "Status",
        STATUS_LIST.map { it.second }.toTypedArray()
    )

    protected class CensureFilter : Filter.Select<String>(
        "Censura",
        CENSURE_LIST.map { it.second }.toTypedArray()
    )

    protected class SortFilter : Filter.Sort(
        "Ordem",
        SORT_LIST.map { it.third }.toTypedArray(),
        Selection(2, false)
    )

    protected class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Gêneros", genres)
    protected class ExclusiveModeFilter : Filter.CheckBox("Modo Exclusivo", true)

    // [...document.querySelectorAll(".filter_genre.list-item")]
    //     .map(el => `Tag("${el.getAttribute('data-value')}", "${el.innerText}")`).join(',\n')
    protected abstract fun getGenreList(): List<Tag>

    private fun String.changeSize(): String = substringBefore("&w=280") + "&w512"

    private fun String.toStatus() = when {
        contains("Em lançamento") -> SManga.ONGOING
        contains("Sendo upado") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    protected fun Response.asJsonObject(): JsonObject =
        JSON_PARSER.parse(body()!!.string().substringAfter("</b>")).obj

    private fun Map<String, String>.toUrlQueryParams(): String =
        map { (k, v) -> "$k=$v" }.joinToString("&")

    private fun FormBody.value(name: String): String {
        return (0 until size())
            .first { name(it) == name }
            .let { value(it) }
    }

    companion object {
        private const val ACCEPT_COMMON = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36"

        private val JSON_PARSER by lazy { JsonParser() }

        private val LETTER_LIST = listOf("Todas", "Caracteres Especiais")
            .plus(('A'..'Z').map { it.toString() })
            .toTypedArray()

        private val STATUS_LIST = listOf(
            Pair("0", "Sem filtro"),
            Pair("complete", "Completo"),
            Pair("progress", "Em progresso"),
            Pair("incomplete", "Incompleto")
        )

        private val CENSURE_LIST = listOf(
            Pair("0", "Sem filtro"),
            Pair("yes", "Sem censura"),
            Pair("no", "Com censura")
        )

        private val SORT_LIST = listOf(
            Triple("a-z", "z-a", "Nome"),
            Triple("date-asc", "date-desc", "Data"),
            Triple("less_access", "more_access", "Popularidade"),
            Triple("less_like", "more_like", "Nº de likes"),
            Triple("post-less", "post-more", "Nº de capítulos")
        )
    }
}
