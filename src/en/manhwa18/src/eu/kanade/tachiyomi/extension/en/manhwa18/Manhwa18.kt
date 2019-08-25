package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class Manhwa18 : ParsedHttpSource() {

    override val name = "Manhwa18"

    override val baseUrl = "https://manhwa18.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "div.row.top div.media"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/manga-list.html?page=$page&ungenre=raw&sort=last_update&sort_type=DESC")

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("h3 a")
        manga.setUrlWithoutDomain("/" + item.attr("href"))
        manga.title = item.text()
        when {
            element.select("img").attr("src").contains("file-thumb") ->
                manga.thumbnail_url = "http:" + element.select("img").attr("src")
            element.select("img").attr("src").contains("app/manga/uploads") ->
                manga.thumbnail_url = baseUrl + element.select("img").attr("src")
            else -> manga.thumbnail_url = element.select("img").attr("src")
        }
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a:not(.disabled):contains(»)"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/manga-list.html?page=$page&ungenre=raw&sort=views&sort_type=DESC")

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga-list.html")!!.newBuilder()
        url.addQueryParameter("listType", "pagination")
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("name", query)
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is StatusFilter -> {
                    if(filter.state != 0) {
                        url.addQueryParameter("m_status", filter.toUriPart())
                    }
                }
                is SortBy -> {
                    url.addQueryParameter("sort", when {
                        filter.state?.index == 0 -> "name"
                        filter.state?.index == 1 -> "views"
                        else -> "last_update"
                    })
                    if (filter.state?.ascending == true)
                        url.addQueryParameter("sort_type", "ASC")
                }
                is GenreFilter -> {
                    val genreToExclude = mutableListOf<String>()
                    val genreToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.isExcluded())
                            genreToExclude.add(content.name)
                        else if (content.isIncluded())
                            genreToInclude.add(content.name)
                    }
                    if (genreToExclude.isNotEmpty()) {
                        genreToExclude.add("raw")
                        url.addQueryParameter("ungenre", genreToExclude
                            .joinToString(","))
                    } else url.addQueryParameter("ungenre", "raw")
                    if (genreToInclude.isNotEmpty()) {
                        url.addQueryParameter("genre", genreToInclude
                            .joinToString(","))
                    }
                }
                is TypeFilter -> {
                    if(filter.state != 0) {
                        url.addQueryParameter("genre", filter.toUriPart())
                    }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("ul.manga-info")
        val manga = SManga.create()
        val status = infoElement.select("b:contains(status) + a").text()
        val genres = mutableListOf<String>()
        infoElement.select("b:contains(genre) + small a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        val authors = mutableListOf<String>()
        infoElement.select("b:contains(author) + small a").forEach { element ->
            val author = element.text()
            authors.add(author)
        }
        manga.title = infoElement.select("h1").text()
        manga.author = authors.joinToString(", ")
        manga.artist = authors.joinToString(", ")
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.description = document.select("div.row:contains(description)").text()
            .substringAfter("Description ")
        when {
            document.select("img.thumbnail").attr("src").contains("file-thumb") ->
                manga.thumbnail_url = "http:" + document.select("img.thumbnail").attr("src")
            document.select("img.thumbnail").attr("src").contains("app/manga/uploads") ->
                manga.thumbnail_url = baseUrl + document.select("img.thumbnail").attr("src")
            else -> manga.thumbnail_url = document.select("img.thumbnail").attr("src")
        }
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("On going") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "table tr"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val timeElement = element.select("time").first()
        chapter.setUrlWithoutDomain("/" + element.select("a.chapter").attr("href"))
        chapter.name = element.select("a.chapter").text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()
        return when {
            "minutes" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hours" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "days" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "weeks" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "months" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "years" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("img.chapter-img").forEach {
            val img = if (!it.attr("src").contains("app/manga/uploads"))
                it.attr("src")
            else
                "$baseUrl/" + it.attr("src")
            pages.add(Page(pages.size, "", img))
        }
        return pages

    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    private class AuthorFilter : Filter.Text("Author(s)")

    private class StatusFilter : UriPartFilter("Status", arrayOf(
            Pair("Any", ""),
            Pair("Completed", "1"),
            Pair("On going", "2"),
            Pair("Drop", "3")
    ))

    private class SortBy : Filter.Sort("Sorted By", arrayOf("A-Z", "Most vỉews", "Last updated"),
        Selection(1, false))

    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)

    override fun getFilterList() = FilterList(
            AuthorFilter(),
            StatusFilter(),
            TypeFilter(),
            SortBy(),
            GenreFilter(getGenreList())
    )

    private fun getGenreList() = listOf(
            Tag("Action"),
            Tag("Anime"),
            Tag("Comedy"),
            Tag("Comic"),
            Tag("Doujinshi"),
            Tag("Drama"),
            Tag("Ecchi"),
            Tag("Fantasy"),
            Tag("Gender Bender"),
            Tag("Harem"),
            Tag("Historical"),
            Tag("Horror"),
            Tag("Josei"),
            Tag("Manhua"),
            Tag("Martial Art"),
            Tag("Mature"),
            Tag("Mecha"),
            Tag("Mystery"),
            Tag("One shot"),
            Tag("Psychological"),
            Tag("Romance"),
            Tag("School Life"),
            Tag("Sci-fi"),
            Tag("Seinen"),
            Tag("Shoujo"),
            Tag("Shojou Ai"),
            Tag("Shounen"),
            Tag("Shounen Ai"),
            Tag("Slice of Life"),
            Tag("Smut"),
            Tag("Sports"),
            Tag("Supernatural"),
            Tag("Tragedy"),
            Tag("Adventure"),
            Tag("Yaoi")
    )

    private class TypeFilter : UriPartFilter("Types", arrayOf(
            Pair("<select>", ""),
            Pair("Adult 18", "adult"),
            Pair("Manhwa", "manhwa"),
            Pair("Hentai", "hentai")
    ))

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(name: String) : Filter.TriState(name)
}
