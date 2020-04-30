package eu.kanade.tachiyomi.extension.all.simplyhentai

import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class SimplyHentai(
    override val lang: String,
    private val urlLang: String,
    private val searchLang: String
) : ParsedHttpSource() {
    override val name = "Simply Hentai"

    override val baseUrl = "https://www.simply-hentai.com"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val gson = Gson()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/album/language/$urlLang/$page/popularity/desc", headers)
    }

    override fun popularMangaSelector() = "div.col-sm-3"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("h3.object-title a").let {
            manga.url = it.attr("href").substringAfter(baseUrl)
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img.img-responsive").attr("abs:data-src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/album/language/$urlLang/$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("language_ids[$searchLang]", searchLang)
            .addQueryParameter("page", page.toString())

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.forEach {
                        if (it.state) url.addQueryParameter("tag_ids[${it.id}]", it.id)
                    }
                }
                is SeriesList -> {
                    filter.state.forEach {
                        if (it.state) url.addQueryParameter("series_id[${it.id}]", it.id)
                    }
                }
                is SortOrder -> {
                    url.addQueryParameter("sort", getSortOrder()[filter.state].second)
                }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        document.select("div.padding-md-right-8").let { info ->
            manga.artist = info.select("div.box-title:contains(Artists) + a").text()
            manga.author = manga.artist
            manga.genre = info.select("a[rel=tag]").joinToString { it.text() }
            manga.description = info.select("div.link-box > div.box-title:contains(Series) ~ a").let { e ->
                if (e.text().isNotEmpty()) "Series: ${e.joinToString { it.text() }}\n\n" else ""
            }
            manga.description += info.select("div.link-box > div.box-title:contains(Characters) ~ a").let { e ->
                if (e.text().isNotEmpty()) ("Characters: ${e.joinToString { it.text() }}\n\n") else ""
            }
        }
        manga.thumbnail_url = document.select("div.col-xs-12 img.img-responsive").attr("abs:data-src")

        return manga
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapter = SChapter.create().apply {
            name = "Chapter"
            url = response.request().url().toString().removeSuffix("/").substringAfterLast("/")
        }
        return listOf(chapter)
    }

    override fun chapterListSelector() = "not used"

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("https://api.simply-hentai.com/v1/images/album/${chapter.url}", headersBuilder().add("X-Requested-With", "XMLHttpRequest").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()

        gson.fromJson<JsonObject>(response.body()!!.string()).forEach { _, jsonElement ->
            pages.add(Page(pages.size, "", jsonElement["sizes"]["full"].string))
        }

        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    private class SortOrder(sortPairs: List<Pair<String, String>>) : Filter.Select<String>("Sort By", sortPairs.map { it.first }.toTypedArray())
    private class SearchPair(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreList(searchVal: List<SearchPair>) : Filter.Group<SearchPair>("Genres", searchVal)
    private class SeriesList(searchVal: List<SearchPair>) : Filter.Group<SearchPair>("Series", searchVal)

    override fun getFilterList() = FilterList(
        SortOrder(getSortOrder()),
        GenreList(getGenreList()),
        SeriesList(getSeriesList())
    )

    // "Relevance" should be empty, don't add a "Views" sort order
    private fun getSortOrder() = listOf(
        Pair("Relevance", ""),
        Pair("Popularity", "sort_value"),
        Pair("Upload Date", "created_at")
    )

    // TODO: add more to getGenreList and getSeriesList
    private fun getGenreList() = listOf(
        SearchPair("Solo Female", "4807"),
        SearchPair("Solo Male", "4805"),
        SearchPair("Big Breasts", "2528"),
        SearchPair("Nakadashi", "2418"),
        SearchPair("Blowjob", "64"),
        SearchPair("Schoolgirl Uniform", "2522"),
        SearchPair("Stockings", "33")
    )

    private fun getSeriesList() = listOf(
        SearchPair("Original Work", "1093"),
        SearchPair("Kantai Collection", "1316"),
        SearchPair("Touhou", "747"),
        SearchPair("Fate Grand Order", "2171"),
        SearchPair("Idolmaster", "306"),
        SearchPair("Granblue Fantasy", "2041"),
        SearchPair("Girls Und Panzer", "1324")
    )
}
