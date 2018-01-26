package eu.kanade.tachiyomi.extension.en.mangadex

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

class Mangadex : ParsedHttpSource() {

    override val name = "MangaDex"

    override val baseUrl = "https://mangadex.com"

    override val supportsLatest = true

    override val lang = "en"

    private val internalLang = "gb"

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = ".table-responsive tbody tr"

    override fun latestUpdatesSelector() = ".table-responsive tbody tr a.manga_title[href*=manga]"

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "//" + ((page * 100) - 100) else ""
        return GET("$baseUrl/titles$pageStr", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a[href*=manga]").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
            manga.author = it?.text()?.trim()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = ".pagination li:not(.disabled) span[title*=last page]:not(disabled)"

    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/?page=search")!!.newBuilder().addQueryParameter("title", query)
        val genres = mutableListOf<String>()

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is GenreList -> filter.state.forEach { genre ->
                    when (genre.state) {
                        true -> genres.add(genre.id)
                    }
                }
            }
        }
        if (genres.isNotEmpty()) url.addQueryParameter("genre", genres.joinToString(","))

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = ".table.table-striped.table-hover.table-condensed tbody tr"

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val imageElement = document.select(".table-condensed").first()
        val infoElement = document.select(".table.table-condensed.edit").first()
        val genreElement = infoElement.select("tr:eq(3) td .genre")

        manga.author = infoElement.select("tr:eq(1) td").first()?.text()
        manga.artist = infoElement.select("tr:eq(2) td").first()?.text()
        manga.status = parseStatus(infoElement.select("tr:eq(5) td").first()?.text())
        manga.description = infoElement.select("tr:eq(7) td").first()?.text()
        manga.thumbnail_url = imageElement.select("img").first()?.attr("src").let { baseUrl + "/" + it }
        var genres = mutableListOf<String>()
        genreElement.forEach { genres.add(it.text()) }
        manga.genre = genres.joinToString(", ")

        return manga
    }

    override fun chapterListSelector() = ".table.table-striped.table-hover.table-condensed tbody tr:has(img[src*=$internalLang])"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td:eq(0)").first()
        val dateElement = element.select("td:eq(6)").first()
        val scanlatorElement = element.select("td:eq(2)").first()

        val chapter = SChapter.create()
        chapter.url = (urlElement.select("a").attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = dateElement?.attr("title")?.let { parseChapterDate(it.removeSuffix(" UTC")) } ?: 0
        chapter.scanlator = scanlatorElement?.text()
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(date).time
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val url = document.baseUri()
        document.select("#jump_page").first().select("option").forEach {
            pages.add(Page(pages.size, url + "/" + it.attr("value")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        val attr = document.select("#current_page").first().attr("src")

        if (attr.startsWith("http")) {
            return attr
        }
        return baseUrl + attr
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        status.contains("Licensed") -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Genre(val id: String, name: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)


    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
            Genre("1", "4-koma"),
            Genre("2", "Action"),
            Genre("3", "Adventure"),
            Genre("4", "Award Winning"),
            Genre("5", "Comedy"),
            Genre("6", "Cooking"),
            Genre("7", "Doujinshi"),
            Genre("8", "Drama"),
            Genre("9", "Ecchi"),
            Genre("10", "Fantasy"),
            Genre("11", "Gender Bender"),
            Genre("12", "Harem"),
            Genre("13", "Historical"),
            Genre("14", "Horror"),
            Genre("15", "Josei"),
            Genre("16", "Martial Arts"),
            Genre("17", "Mecha"),
            Genre("18", "Medical"),
            Genre("19", "Music"),
            Genre("20", "Mystery"),
            Genre("21", "Oneshot"),
            Genre("22", "Psychological"),
            Genre("23", "Romance"),
            Genre("24", "School Life"),
            Genre("25", "Sci-Fi"),
            Genre("26", "Seinen"),
            Genre("27", "Shoujo"),
            Genre("28", "Shoujo Ai"),
            Genre("29", "Shounen"),
            Genre("30", "Shounen Ai"),
            Genre("31", "Slice of Life"),
            Genre("32", "Smut"),
            Genre("33", "Sports"),
            Genre("34", "Supernatural"),
            Genre("35", "Tragedy"),
            Genre("36", "Webtoon"),
            Genre("37", "Yaoi"),
            Genre("38", "Yuri"),
            Genre("39", "[no chapters]"),
            Genre("40", "Game")
    )
}