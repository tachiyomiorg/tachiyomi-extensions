package eu.kanade.tachiyomi.extension.en.manga1s

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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class manga1s : ParsedHttpSource() {

    override val name = "Manga1s"

    override val baseUrl = "https://manga1s.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search/?s=rank&compact=1&page=$page#paged", headers)
    }

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {

        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("p.manga-list-4-item-title > a").text()
        manga.thumbnail_url = element.select("a img").attr("data-src")

        return manga
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/search/?s=news&compact=1&page=$page#paged", headers)
    }

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga {

        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("p.manga-list-4-item-title > a").text()
        manga.thumbnail_url = element.select("a img").attr("data-src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("text", query)
        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genreInclude = filter.state.filter { it.isIncluded() }.joinToString { it.id }
                    val genreExclude = filter.state.filter { it.isExcluded() }.joinToString { it.id }
                    url.addQueryParameter("genres", genreInclude)
                    url.addQueryParameter("excludes", genreExclude)
                }
                is SortFilter -> url.addQueryParameter("s", filter.toUriPart())
            }
        }
        url.addQueryParameter("compact", "1")
        url.addQueryParameter("page", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "ul > li:nth-child(n)"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("p.manga-list-4-item-title > a").text()
        manga.thumbnail_url = element.select("a img").attr("data-src")

        return manga
    }

    override fun searchMangaNextPageSelector() = "div.pager-list > div > a:nth-child(8)"

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("div.detail-info-right > h1").text()
        description = document.select("div.detail-info-right > p.fullcontent").text()
        thumbnail_url = document.select("div.detail-info-cover > img").attr("data-src")
        author = document.select("p.detail-info-right-say > a").text()
        genre = document.select("p.detail-info-right-tag-list > a:nth-child(n)").joinToString { it.text() }
        status = document.select("span.detail-info-right-title-tip").text().let {
            when {
                it.contains("Ongoing") -> SManga.ONGOING
                it.contains("Completed") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters
    override fun chapterListSelector() = "div.chap-version.version-active #list-2 > ul > li:nth-child(n)"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        chapter.name = element.select("a").attr("title")

        return chapter
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div.reader-main > img:nth-child(n)").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("data-src")))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    // Filter
    override fun getFilterList() = FilterList(
        GenreList(genres()),
        SortFilter()
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("popularity", "Popularity"),
            Pair("alphabetical", "A-Z"),
            Pair("rating", "Rating"),
            Pair("chapters", "Chapters"),
            Pair("news", "Last Update"),
            Pair("rank", "Rank"),
            Pair("hourly", "Hourly View"),
            Pair("daily", "Daily View")
        )
    )

    private fun genres(): List<Genre> = listOf(
        Genre("4Koma", "4-Koma"),
        Genre("Action", "Action"),
        Genre("Adult", "Adult"),
        Genre("Adventure", "Adventure"),
        Genre("Comedy", "Comedy"),
        Genre("Doujinshi", "Doujinshi"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Fantasy", "Fantasy"),
        Genre("Full Color", "full-color"),
        Genre("Gender bender", "Gender-bender"),
        Genre("Harem", "Harem"),
        Genre("Hentai", "Hentai"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Josei", "Josei"),
        Genre("Loli", "Loli"),
        Genre("Long Strip", "Long-strip"),
        Genre("Magic", "Magic"),
        Genre("Mature", "Mature"),
        Genre("Martial arts", "Martial-arts"),
        Genre("Mecha", "Mecha"),
        Genre("Mystery", "Mystery"),
        Genre("One shot", "One-Shot"),
        Genre("Psychological", "Psychological"),
        Genre("Romance", "Romance"),
        Genre("School life", "School-life"),
        Genre("Shoujo", "Shoujo"),
        Genre("Slice of life", "Slice-of-life"),
        Genre("Supernatural", "Supernatural"),
        Genre("Shounen", "Shounen"),
        Genre("Seinen", "Seinen"),
        Genre("Sci-fi", "Sci-fi"),
        Genre("Shounen ai", "Shounen-ai"),
        Genre("Smut", "Smut"),
        Genre("Shoujo ai", "Shoujo-ai"),
        Genre("Sports", "Sports"),
        Genre("Tragedy", "Tragedy"),
        Genre("Web comic", "web-comic"),
        Genre("Webtoon", "Webtoon"),
        Genre("Yaoi", "Yaoi"),
        Genre("Yuri", "Yuri")
    )
}
