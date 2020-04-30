package eu.kanade.tachiyomi.extension.pt.hqnow

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class HQNow : HttpSource() {

    override val name = "HQ Now!"

    // Website is http://www.hq-now.com
    override val baseUrl = "http://admin.hq-now.com/graphql"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    private val jsonHeaders = headersBuilder().add("content-type", "application/json").build()

    private fun mangaFromResponse(response: Response, selector: String, coversAvailable: Boolean = true): List<SManga> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["data"][selector].asJsonArray
            .map {
                SManga.create().apply {
                    url = it["id"].asString
                    title = it["name"].asString
                    if (coversAvailable) thumbnail_url = it["hqCover"].asString
                }
            }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return POST(baseUrl, jsonHeaders, RequestBody.create(null, "{\"operationName\":\"getHqsByFilters\",\"variables\":{\"orderByViews\":true,\"loadCovers\":true,\"limit\":30},\"query\":\"query getHqsByFilters(\$orderByViews: Boolean, \$limit: Int, \$publisherId: Int, \$loadCovers: Boolean) {\\n  getHqsByFilters(orderByViews: \$orderByViews, limit: \$limit, publisherId: \$publisherId, loadCovers: \$loadCovers) {\\n    id\\n    name\\n    editoraId\\n    status\\n    publisherName\\n    hqCover\\n    synopsis\\n    updatedAt\\n  }\\n}\\n\"}"))
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return MangasPage(mangaFromResponse(response, "getHqsByFilters"), false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return POST(baseUrl, jsonHeaders, RequestBody.create(null, "{\"operationName\":\"getRecentlyUpdatedHqs\",\"variables\":{},\"query\":\"query getRecentlyUpdatedHqs {\\n  getRecentlyUpdatedHqs {\\n    name\\n    hqCover\\n    synopsis\\n    id\\n    updatedAt\\n    updatedChapters\\n  }\\n}\\n\"}"))
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(mangaFromResponse(response, "getRecentlyUpdatedHqs"), false)
    }

    // Search

    private var queryIsTitle = true

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            queryIsTitle = true
            POST(baseUrl, jsonHeaders, RequestBody.create(null, "{\"operationName\":\"getHqsByName\",\"variables\":{\"name\":\"$query\"},\"query\":\"query getHqsByName(\$name: String!) {\\n  getHqsByName(name: \$name) {\\n    id\\n    name\\n    editoraId\\n    status\\n    publisherName\\n    impressionsCount\\n  }\\n}\\n\"}"))
        } else {
            queryIsTitle = false
            var searchLetter = ""

            filters.forEach { filter ->
                when (filter) {
                    is LetterFilter -> {
                        searchLetter = filter.toUriPart()
                    }
                }
            }
            POST(baseUrl, jsonHeaders, RequestBody.create(null, "{\"operationName\":\"getHqsByNameStartingLetter\",\"variables\":{\"letter\":\"$searchLetter-$searchLetter\"},\"query\":\"query getHqsByNameStartingLetter(\$letter: String!) {\\n  getHqsByNameStartingLetter(letter: \$letter) {\\n    id\\n    name\\n    editoraId\\n    status\\n    publisherName\\n    impressionsCount\\n  }\\n}\\n\"}"))
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return MangasPage(mangaFromResponse(response, if (queryIsTitle) "getHqsByName" else "getHqsByNameStartingLetter", false), false)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return POST(baseUrl, jsonHeaders, RequestBody.create(null, "{\"operationName\":\"getHqsById\",\"variables\":{\"id\":${manga.url}},\"query\":\"query getHqsById(\$id: Int!) {\\n  getHqsById(id: \$id) {\\n    id\\n    name\\n    synopsis\\n    editoraId\\n    status\\n    publisherName\\n    hqCover\\n    impressionsCount\\n    capitulos {\\n      name\\n      id\\n      number\\n    }\\n  }\\n}\\n\"}"))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return gson.fromJson<JsonObject>(response.body()!!.string())["data"]["getHqsById"][0]
            .let {
                SManga.create().apply {
                    title = it["name"].asString
                    thumbnail_url = it["hqCover"].asString
                    description = it["synopsis"].asString
                    author = it["publisherName"].asString
                    status = when (it["status"].asString) {
                        "Concluído" -> SManga.COMPLETED
                        "Em Andamento" -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                }
            }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["data"]["getHqsById"][0]["capitulos"].asJsonArray
            .map {
                SChapter.create().apply {
                    url = it["id"].asString
                    name = it["name"].asString.let { jsonName ->
                        if (jsonName.isNotEmpty()) jsonName.trim() else "Capitulo: " + it["number"].asString
                    }
                }
            }.reversed()
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return POST(baseUrl, jsonHeaders, RequestBody.create(null, "{\"operationName\":\"getChapterById\",\"variables\":{\"chapterId\":${chapter.url}},\"query\":\"query getChapterById(\$chapterId: Int!) {\\n  getChapterById(chapterId: \$chapterId) {\\n    name\\n    number\\n    oneshot\\n    pictures {\\n      pictureUrl\\n    }\\n    hq {\\n      id\\n      name\\n      capitulos {\\n        id\\n        number\\n      }\\n    }\\n  }\\n}\\n\"}"))
    }

    override fun pageListParse(response: Response): List<Page> {
        return gson.fromJson<JsonObject>(response.body()!!.string())["data"]["getChapterById"]["pictures"].asJsonArray
            .mapIndexed { i, json -> Page(i, "", json["pictureUrl"].asString) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTA: Ignorado se estiver usando"),
        Filter.Header("a pesquisa de texto!"),
        Filter.Separator(),
        LetterFilter()
    )

    private class LetterFilter : UriPartFilter("Letra", arrayOf(
        Pair("---", "<Selecione>"),
        Pair("a", "A"),
        Pair("b", "B"),
        Pair("c", "C"),
        Pair("d", "D"),
        Pair("e", "E"),
        Pair("f", "F"),
        Pair("g", "G"),
        Pair("h", "H"),
        Pair("i", "I"),
        Pair("j", "J"),
        Pair("k", "K"),
        Pair("l", "L"),
        Pair("m", "M"),
        Pair("n", "N"),
        Pair("o", "O"),
        Pair("p", "P"),
        Pair("q", "Q"),
        Pair("r", "R"),
        Pair("s", "S"),
        Pair("t", "T"),
        Pair("u", "U"),
        Pair("v", "V"),
        Pair("w", "W"),
        Pair("x", "X"),
        Pair("y", "Y"),
        Pair("z", "Z")
    ))

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }
}
