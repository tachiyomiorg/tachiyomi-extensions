package eu.kanade.tachiyomi.extension.en.nyahentai

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
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

@Nsfw
class NyaHentai : ParsedHttpSource() {
    companion object {
        const val TAG = "NyaHentai"
    }

    override val name = "NyaHentai (en)"

    override val baseUrl = "https://nyahentai.com"

    val languageUrl = "$baseUrl/language/english"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "div.container div.gallery a"

    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            GET(languageUrl, headers)
        } else {
            GET("$languageUrl/page/$page", headers)
        }
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
                setUrlWithoutDomain(response.request().url().toString())
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
        GET("$languageUrl/popular/page/$page", headers)

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    private lateinit var tagUrl: String

    // TODO: Additional filter options, specifically the type[] parameter
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/search/q_$query/page/$page"

        if (query.isBlank()) {
            filters.forEach { filter ->
                when (filter) {
                    is Tag -> {
                        url = if (page == 1) {
                            "$baseUrl/tag/${filter.state}&type[]=3" // "Contents" tag
                        } else {
                            "$tagUrl/page/$page"
                        }
                    }
                }
            }
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request().url().toString().contains("tag?")) {
            response.asJsoup().select("table.table tbody tr a:first-of-type").attr("abs:href").let {
                if (it.isNotEmpty()) {
                    tagUrl = it
                    super.searchMangaParse(client.newCall(GET(tagUrl, headers)).execute())
                } else {
                    MangasPage(emptyList(), false)
                }
            }
        } else {
            super.searchMangaParse(response)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Tag("Tag")
    )

    private class Tag(name: String) : Filter.Text(name)
}
