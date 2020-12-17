package eu.kanade.tachiyomi.extension.en.mangapill

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

class MangaPill : ParsedHttpSource() {

    override val name = "MangaPill"
    override val baseUrl = "https://mangapill.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search?page=$page&title=&type=&status=1", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl", headers)
    }

    override fun popularMangaSelector() = ".grid.justify-between.gap-3.grid-cols-2 > div"
    override fun latestUpdatesSelector() = ".w-full.flex.rounded.border.border-sm.border-color-border-primary.mb-2"
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("data-src")
        manga.setUrlWithoutDomain(element.select("a").first().attr("href"))
        manga.title = element.select(".mt-2 a").text()
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("data-src")
        var url = element.select("a").first().attr("href")
        manga.setUrlWithoutDomain(url.substringBeforeLast("/").replace("chapters", "manga").substringBeforeLast("-") + "/" + url.substringAfterLast("/").substringBefore("-chapter"))
        manga.title = element.select(".mb-2 a").text().substringBefore("Chapter").trim()
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select(".flex.flex-col > div:nth-child(1) > .text-color-text-secondary").text()
        manga.artist = ""
        val genres = mutableListOf<String>()
        document.select("a[href*=genre]").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(document.select("h5:contains(Status) + div").text())
        manga.description = document.select(".flex.flex-col > div p").first().text()
        manga.thumbnail_url = document.select(".object-cover").first().attr("data-src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {

        element.toLowerCase().contains("publishing") -> SManga.ONGOING
        element.toLowerCase().contains("finished") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "option[value]"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.attr("value")
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement)
        chapter.name = element.text()
        chapter.date_upload = 0
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("picture img").forEachIndexed { i, it ->
            pages.add(Page(i, "", it.attr("data-src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {

        val url = HttpUrl.parse("$baseUrl/search")!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("title", query)

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {

                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre", genre)
                        }
                    }
                }
                is Status -> url.addQueryParameter("status", filter.toUriPart())
                is Type -> url.addQueryParameter("type", filter.toUriPart())
            }
        }
        return GET(url.toString(), headers)
    }

    private class Type : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "1"),
            Pair("Novel", "2"),
            Pair("One-Shot", "3"),
            Pair("Doujinshi", "4"),
            Pair("Manhwa", "5"),
            Pair("Manhua", "6"),
            Pair("Oel", "7")
        )
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Publishing", "1"),
            Pair("Finished", "2"),
            Pair("On Hiatus", "3"),
            Pair("Discontinued", "4"),
            Pair("Not yet Published", "5")
        )
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Status(),
        Type(),
        GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("Action", "1"),
        Genre("Adventure", "2"),
        Genre("Cars", "3"),
        Genre("Comedy", "4"),
        Genre("Dementia", "5"),
        Genre("Demons", "6"),
        Genre("Drama", "8"),
        Genre("Ecchi", "9"),
        Genre("Fantasy", "10"),
        Genre("Game", "11"),
        Genre("Harem", "35"),
        Genre("Hentai", "12"),
        Genre("Historical", "13"),
        Genre("Horror", "14"),
        Genre("Josei", "43"),
        Genre("Kids", "15"),
        Genre("Magic", "16"),
        Genre("Martial Arts", "17"),
        Genre("Mecha", "18"),
        Genre("Military", "38"),
        Genre("Music", "19"),
        Genre("Mystery", "7"),
        Genre("Parody", "20"),
        Genre("Police", "39"),
        Genre("Psychological", "40"),
        Genre("Romance", "22"),
        Genre("Samurai", "21"),
        Genre("School", "23"),
        Genre("Sci-Fi", "24"),
        Genre("Seinen", "42"),
        Genre("Shoujo", "25"),
        Genre("Shoujo Ai", "26"),
        Genre("Shounen", "27"),
        Genre("Shounen Ai", "28"),
        Genre("Slice of Life", "36"),
        Genre("Space", "29"),
        Genre("Sports", "30"),
        Genre("Super Power", "31"),
        Genre("Supernatural", "37"),
        Genre("Thriller", "41"),
        Genre("Vampire", "32"),
        Genre("Yaoi", "33"),
        Genre("Yuri", "34")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
