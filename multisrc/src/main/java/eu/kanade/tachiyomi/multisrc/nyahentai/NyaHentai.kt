package eu.kanade.tachiyomi.multisrc.nyahentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

abstract class NyaHentai(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {
    companion object {
        private const val NOT_FOUND_MESSAGE = "If you used a filter, check if the keyword actually exists."
        private const val Filter_SEARCH_MESSAGE = "NOTE: Ignored if using text search!"
        const val PREFIX_ID_SEARCH = "id:"
    }

    val nyaLang = when (lang) {
        "en" -> "english"
        "zh" -> "chinese"
        "ja" -> "japanese"
        else -> ""
    }

    val languageUrl = when (nyaLang) {
        "" -> baseUrl
        else -> "$baseUrl/language/$nyaLang"
    }

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "div.container div.gallery a"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$languageUrl/page/$page", headers)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.caption").text()
        manga.thumbnail_url = element.select("img.lazyload").attr("abs:data-src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "section.pagination a[rel=next]"

    private fun parseTAG(tag: String): String = tag.replace("\\((.*)\\)".toRegex(), "").trim()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#bigcontainer.container")
        val manga = SManga.create()
        val genres = mutableListOf<String>()

        infoElement.select("div.tag-container:contains(Tags) a").forEach { element ->
            val genre = parseTAG(element.text())
            genres.add(genre)
        }

        manga.title = infoElement.select("h1").text()
        manga.author = ""
        manga.artist = parseTAG(infoElement.select("div.tag-container:contains(Artists) a").text())
        manga.status = SManga.COMPLETED
        manga.genre = genres.joinToString(", ")
        manga.thumbnail_url = infoElement.select("div#cover a img.lazyload").attr("abs:data-src")

        manga.description = getDesc(document)

        return manga
    }

    private fun getDesc(document: Document): String {
        val infoElement = document.select("div#bigcontainer.container")

        val pages =
            infoElement.select("div#info > div:contains(pages)")?.text()?.replace(" pages", "")

        val multiDescriptions = listOf(
            "Parodies",
            "Characters",
            "Groups",
            "Languages",
            "Categories"
        ).map {
            it to infoElement.select("div.tag-container:contains($it) a")
                .map { v -> parseTAG(v.text()) }
        }
            .filter { !it.second.isNullOrEmpty() }
            .map { "${it.first}: ${it.second.joinToString()}" }

        val descriptions = listOf(
            multiDescriptions.joinToString("\n\n"),
            pages?.let { "Pages: $it" }
        )

        return descriptions.joinToString("\n\n")
    }

    override fun chapterListParse(response: Response) = with(response.asJsoup()) {
        listOf(
            SChapter.create().apply {
                name = "Single Chapter"
                setUrlWithoutDomain(response.request.url.toString())
            }
        )
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}list/1/")

    override fun chapterListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException("Not used")

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val imageUrl = document.select(".container img.current-img").attr("abs:src")

        val idRegex = "(.*)/galleries\\/(\\d+)\\/(\\d*)\\.(\\w+)".toRegex()
        val match = idRegex.find(imageUrl)

        val base = match?.groups?.get(1)?.value
        val id = match?.groups?.get(2)?.value
        val ext = match?.groups?.get(4)?.value

        val total: Int = (document.select("#pagination-page-top .num-pages").text()).toInt()

        for (i in 1..total) {
            pages.add(Page(i, "", "$base/galleries/$id/$i.$ext"))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    override fun popularMangaRequest(page: Int): Request =
        GET(
            when (nyaLang) {
                "" -> "$languageUrl/page/$page"
                else -> "$languageUrl/popular/page/$page"
            },
            headers
        )

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            // Normal search
            return GET("$baseUrl/search/q_$query $nyaLang/page/$page", headers)
        } else {
            val type = filters.filterIsInstance<TypeFilter>()
                .joinToString("") {
                    (it as UriPartFilter).toUriPart()
                }
            val keyword = filters.filterIsInstance<Text>().toString()
                .replace("[", "").replace("]", "")
            var sort = nyaLang
            if (nyaLang == "") {
                sort = filters.filterIsInstance<SortFilter>()
                    .joinToString("") {
                        (it as UriPartFilter).toUriPart()
                    }
            }
            val url = "$baseUrl/$type/$keyword/$sort/page/$page"
            return GET(url, headers)
        }
    }

    // For NyaHentaiUrlActivity
    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val sManga = mangaDetailsParse(response)
        sManga.url = "/g/$id/"
        return MangasPage(listOf(sManga), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.isSuccessful) {
            response.close()
            throw Exception(NOT_FOUND_MESSAGE)
        }
        return super.searchMangaParse(response)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun getFilterList(): FilterList {
        if (nyaLang == "") {
            return FilterList(
                Filter.Header(Filter_SEARCH_MESSAGE),
                Filter.Separator(),
                SortFilter(),
                TypeFilter(),
                Text("Keyword")
            )
        } else {
            return FilterList(
                Filter.Header(Filter_SEARCH_MESSAGE),
                Filter.Separator(),
                TypeFilter(),
                Text("Keyword")
            )
        }
    }

    private open class UriPartFilter(
        displayName: String,
        val pair: Array<Pair<String, String>>,
        defaultState: Int = 0
    ) : Filter.Select<String>(displayName, pair.map { it.first }.toTypedArray(), defaultState) {
        open fun toUriPart() = pair[state].second
    }

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("Tag", "tag"),
            Pair("Parody", "parody"),
            Pair("Character", "character"),
            Pair("Artist", "artist"),
            Pair("Group", "group")
        )
    )

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Time", ""),
            Pair("Popular", "popular"),
        )
    )

    private class Text(name: String) : Filter.Text(name) {
        override fun toString(): String {
            return state
        }
    }
}
