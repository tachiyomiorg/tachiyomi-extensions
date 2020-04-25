package eu.kanade.tachiyomi.extension.pt.superhentais

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
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mapOf
import kotlin.collections.mutableListOf
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toMutableMap
import kotlin.collections.toTypedArray
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SuperHentais : ParsedHttpSource() {

    override val name = "Super Hentais"

    override val baseUrl = "https://superhentais.com"

    override val lang = "pt"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    private fun genericPaginatedRequest(
        filterData: Map<String, String> = DEFAULT_FILTER,
        filterGenreAdd: List<String> = emptyList(),
        filterGenreDel: List<String> = emptyList(),
        typeUrl: String = "hentai-manga",
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
            .set("Referer", "$baseUrl/hentai-manga")
            .build()

        return POST("$baseUrl/inc/paginator.inc.php", newHeaders, form)
    }

    private fun genericMangaFromElement(element: Element, imageAttr: String = "src"): SManga = SManga.create().apply {
        title = element.select("img").first().attr("alt")
        thumbnail_url = element.select("img").first().attr(imageAttr).changeSize()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaRequest(page: Int): Request = genericPaginatedRequest(page = page)

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
        return MangasPage(mangas, page < totalPage)
    }

    override fun popularMangaSelector(): String = "article.box_view.list div.grid_box div.grid_image.grid_image_vertical a"

    override fun popularMangaFromElement(element: Element): SManga = genericMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        val filterData = DEFAULT_FILTER.toMutableMap()
        filterData["filter_order"] = "date-desc"

        return genericPaginatedRequest(filterData, page = page)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = genericMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val filterList = if (filters.isNotEmpty()) filters else getFilterList()
            val contentIndex = (filterList[0] as ContentFilter).state
            val searchUrl = HttpUrl.parse("$baseUrl/busca")!!.newBuilder()
                .addQueryParameter("formato", CONTENT_LIST[contentIndex].second)
                .addEncodedQueryParameter("parametro", query)
                .toString()

            return GET(searchUrl, headers)
        }

        val newFilterData = DEFAULT_FILTER.toMutableMap()
        val filterGenreAdd = mutableListOf<String>()
        val filterGenreDel = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is ContentFilter -> {
                    newFilterData["filter_type_content"] = CONTENT_LIST[filter.state].first
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

        return genericPaginatedRequest(newFilterData, filterGenreAdd, filterGenreDel, page = page)
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

        title = document.select("div.boxBarraInfo h1").first()!!.text()
        author = infoElement.select("ul.boxAnimeSobre li:contains(Autor) span").first()!!.text()
        artist = infoElement.select("ul.boxAnimeSobre li:contains(Art) span").first()!!.text()
        genre = infoElement.select("ul.boxAnimeSobre li.sizeFull span a").joinToString { it.text() }
        description = document.select("p#sinopse").first()!!.text()
        thumbnail_url = infoElement.select("span.boxAnimeImg img").first()!!.attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val totalPage = document.select("select.pageSelect option").last()!!.attr("value").toInt()
        val idCategory = document.select("div#listaDeConteudo").first()!!.attr("data-id-cat").toInt()
        val mangaUrl = response.request().url().toString()

        val chapters = mutableListOf<SChapter>()

        for (page in 1..totalPage) {
            val result = client.newCall(chapterListPaginatedRequest(idCategory, page, totalPage, mangaUrl)).execute()
            val apiResponse = result.asJsonObject()

            chapters += Jsoup.parse(apiResponse["body"].asJsonArray.joinToString("") { it.string })
                .select(chapterListSelector())
                .map { chapterFromElement(it) }
        }

        return chapters
    }

    private fun chapterListPaginatedRequest(idCategory: Int, page: Int, totalPage: Int, mangaUrl: String): Request {
        val form = FormBody.Builder()
            .add("id_cat", idCategory.toString())
            .add("page", page.toString())
            .add("limit", "50")
            .add("total_page", totalPage.toString())
            .add("order_video", "desc")
            .add("type", "book")
            .build()

        val newHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Type", form.contentType().toString())
            .add("Content-Length", form.contentLength().toString())
            .add("Host", "www." + baseUrl.substringAfter("//"))
            .set("Referer", mangaUrl)
            .build()

        return POST("$baseUrl/inc/paginatorVideo.inc.php", newHeaders, form)
    }

    override fun chapterListSelector() = "div.boxTop10 div.top10Link"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").first()!!.text()
        chapter_number = element.select("span[style]").first()!!.text().toFloatOrNull() ?: 0f
        setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.capituloViewBox img.lazy")
            .mapIndexed { i, element -> Page(i, "", element.absUrl("data-src")) }
    }

    override fun imageUrlParse(document: Document) = ""

    private class Tag(val id: String, name: String) : Filter.TriState(name)

    private class ContentFilter : Filter.Select<String>("Tipo de Conteúdo",
        CONTENT_LIST.map { it.third }.toTypedArray())

    private class LetterFilter : Filter.Select<String>("Letra inicial", LETTER_LIST)

    private class StatusFilter : Filter.Select<String>("Status",
        STATUS_LIST.map { it.second }.toTypedArray())

    private class CensureFilter : Filter.Select<String>("Censura",
        CENSURE_LIST.map { it.second }.toTypedArray())

    private class SortFilter : Filter.Sort("Ordem",
        SORT_LIST.map { it.third }.toTypedArray(), Selection(2, false))

    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Gêneros", genres)
    private class ExclusiveModeFilter : Filter.CheckBox("Modo Exclusivo", true)

    override fun getFilterList() = FilterList(
        ContentFilter(),
        Filter.Header("Filtros abaixo são ignorados na busca!"),
        LetterFilter(),
        StatusFilter(),
        CensureFilter(),
        SortFilter(),
        GenreFilter(getGenreList()),
        ExclusiveModeFilter()
    )

    // [...document.querySelectorAll(".filter_genre.list-item")]
    //     .map(el => `Tag("${el.getAttribute('data-value')}", "${el.innerText}")`).join(',\n')
    private fun getGenreList() = listOf(
        Tag("33", "Ação"),
        Tag("100", "Ahegao"),
        Tag("64", "Anal"),
        Tag("7", "Artes Marciais"),
        Tag("134", "Ashikoki"),
        Tag("233", "Aventura"),
        Tag("57", "Bara"),
        Tag("3", "BDSM"),
        Tag("267", "Big Ass"),
        Tag("266", "Big Cock"),
        Tag("268", "Blowjob"),
        Tag("88", "Boquete"),
        Tag("95", "Brinquedos"),
        Tag("156", "Bukkake"),
        Tag("120", "Chikan"),
        Tag("68", "Comédia"),
        Tag("140", "Cosplay"),
        Tag("265", "Creampie"),
        Tag("241", "Dark Skin"),
        Tag("212", "Demônio"),
        Tag("169", "Drama"),
        Tag("144", "Dupla Penetração"),
        Tag("184", "Enfermeira"),
        Tag("126", "Eroge"),
        Tag("160", "Esporte"),
        Tag("245", "Facial"),
        Tag("30", "Fantasia"),
        Tag("251", "Femdom"),
        Tag("225", "Ficção Científica"),
        Tag("273", "FootJob"),
        Tag("270", "Forçado"),
        Tag("51", "Futanari"),
        Tag("106", "Gang Bang"),
        Tag("240", "Gender Bender"),
        Tag("67", "Gerakuro"),
        Tag("254", "Gokkun"),
        Tag("236", "Golden Shower"),
        Tag("204", "Gore"),
        Tag("234", "Grávida"),
        Tag("148", "Grupo"),
        Tag("2", "Gyaru"),
        Tag("17", "Harém"),
        Tag("8", "Histórico"),
        Tag("250", "Horror"),
        Tag("27", "Incesto"),
        Tag("61", "Jogos Eróticos"),
        Tag("5", "Josei"),
        Tag("48", "Kemono"),
        Tag("98", "Kemonomimi"),
        Tag("252", "Lactação"),
        Tag("177", "Magia"),
        Tag("92", "Maid"),
        Tag("110", "Masturbação"),
        Tag("162", "Mecha"),
        Tag("243", "Menage"),
        Tag("154", "Milf"),
        Tag("115", "Mind Break"),
        Tag("248", "Mind Control"),
        Tag("238", "Mistério"),
        Tag("112", "Moe"),
        Tag("200", "Monstros"),
        Tag("79", "Nakadashi"),
        Tag("46", "Netorare"),
        Tag("272", "Óculos"),
        Tag("1", "Oral"),
        Tag("77", "Paizuri"),
        Tag("237", "Paródia"),
        Tag("59", "Peitões"),
        Tag("274", "Pelos Pubianos"),
        Tag("72", "Pettanko"),
        Tag("36", "Policial"),
        Tag("192", "Professora"),
        Tag("4", "Psicológico"),
        Tag("152", "Punição"),
        Tag("242", "Raio-X"),
        Tag("45", "Romance"),
        Tag("253", "Seinen"),
        Tag("271", "Sex Toys"),
        Tag("93", "Sexo Público"),
        Tag("55", "Shotacon"),
        Tag("9", "Shoujo Ai"),
        Tag("13", "Shounen ai"),
        Tag("239", "Slice of Life"),
        Tag("25", "Sobrenatural"),
        Tag("96", "Superpoder"),
        Tag("158", "Tentáculos"),
        Tag("31", "Terror"),
        Tag("249", "Thriller"),
        Tag("217", "Vampiros"),
        Tag("84", "Vanilla"),
        Tag("23", "Vida Escolar"),
        Tag("40", "Virgem"),
        Tag("247", "Voyeur"),
        Tag("6", "Yaoi"),
        Tag("10", "Yuri")
    )

    private fun String.changeSize(): String = substringBefore("&w=280") + "&w512"

    private fun Response.asJsonObject(): JsonObject = JSON_PARSER.parse(body()!!.string()).obj

    private fun Map<String, String>.toUrlQueryParams(): String =
        map { (k, v) -> "$k=$v" }.joinToString("&")

    private fun FormBody.value(name: String): String {
        return (0 until size())
            .first { name(it) == name }
            .let { value(it) }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }

        private val DEFAULT_FILTER = mapOf(
            "filter_display_view" to "lista",
            "filter_letter" to "0",
            "filter_order" to "more_access",
            "filter_type_content" to "5",
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

        private val LETTER_LIST = listOf("Todas", "Caracteres Especiais")
            .plus(('A'..'Z').map { it.toString() })
            .toTypedArray()

        private val CONTENT_LIST = listOf(
            Triple("5", "hentai-manga", "Hentai Manga"),
            Triple("6", "hq-ero", "HQ Ero"),
            Triple("7", "parody-hentai-manga", "Parody Manga"),
            Triple("8", "parody-hq-ero", "Parody HQ"),
            Triple("9", "doujinshi", "Doujinshi"),
            Triple("10", "manhwa-ero", "Manhwa")
        )

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
