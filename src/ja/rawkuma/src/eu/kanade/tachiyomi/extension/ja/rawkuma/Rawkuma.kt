package eu.kanade.tachiyomi.extension.ja.rawkuma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class Rawkuma: ParsedHttpSource() {

    override val name = "Rawkuma"

    override val baseUrl = "https://rawkuma.com"

    override val lang = "ja"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "div.bsx a"

    override fun latestUpdatesRequest(page: Int):  Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl =  if(page == 1) "$baseUrl/manga/?order=update" else "$baseUrl/manga/page/$page/?order=update"
        return GET(builtUrl)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val imgurl = element.select("img").attr("src").substringBefore("?resize")
        manga.url = element.attr("href")
        manga.title = element.attr("title")
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a.next"

    override fun popularMangaRequest(page: Int):  Request {
        val builtUrl =  if(page == 1) "$baseUrl/manga/?order=popular" else "$baseUrl/manga/page/$page/?order=popular"
        return GET(builtUrl)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl =  if(page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                            .filter { it.state != Filter.TriState.STATE_IGNORE }
                            .forEach { url.addQueryParameter("genre[]", it.id) }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val stringBuilder = StringBuilder()
        val infoElement = document.select("div.infox")
        val author = infoElement.select("span:contains(author)").text().substringAfter("Author: ")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = infoElement.select("span:contains(Status)").text()
        val desc = infoElement.select("span.desc p")
        infoElement.select("span:contains(Genres) a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        if (desc.size > 0) {
            desc.forEach {
                stringBuilder.append(it.text())
                if (it != desc.last())
                    stringBuilder.append("\n\n")
            }
            manga.description = stringBuilder.toString()
        } else
            manga.description = infoElement.select("span.desc").text()

        manga.title = infoElement.select("h1").text()
        manga.author = author
        manga.artist = author
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.thumbnail_url = document.select("div.bigcontent img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Status: Ongoing") -> SManga.ONGOING
        status.contains("Status: Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        // Add date for latest chapter only
        document.select("time[itemprop=dateModified]").attr("datetime")
            .let { chapters[0].date_upload = parseDate(it) }
        return chapters
    }

    private fun parseDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun chapterListSelector() = ".lchx"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")
        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div#readerarea img").forEach {
            val url = it.attr("src")
            pages.add(Page(pages.size, "", url))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
            Filter.Header("You can combine filter."),
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreList(getGenreList())
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter("Type", arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua")
    ))
    private class OrderByFilter : UriPartFilter("Order By", arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular")
    ))

    private fun getGenreList() = listOf(
            Tag("action", "Action"),
            Tag("adult", "Adult"),
            Tag("adventure", "Adventure"),
            Tag("blood", "Blood"),
            Tag("comedy", "Comedy"),
            Tag("drama", "Drama"),
            Tag("ecchi", "Ecchi"),
            Tag("fanta", "Fanta"),
            Tag("fantasy", "Fantasy"),
            Tag("gender-bender", "Gender Bender"),
            Tag("harem", "Harem"),
            Tag("historical", "Historical"),
            Tag("horror", "Horror"),
            Tag("isekai", "Isekai"),
            Tag("josei", "Josei"),
            Tag("lolicon", "Lolicon"),
            Tag("martial-arts", "Martial Arts"),
            Tag("mature", "Mature"),
            Tag("mecha", "Mecha"),
            Tag("mystery", "Mystery"),
            Tag("parody", "Parody"),
            Tag("psychological", "Psychological"),
            Tag("romance", "Romance"),
            Tag("school-life", "School Life"),
            Tag("sci-fi", "Sci-fi"),
            Tag("seinen", "Seinen"),
            Tag("shoujo", "Shoujo"),
            Tag("shoujo-ai", "Shoujo Ai"),
            Tag("shounen", "Shounen"),
            Tag("slice-of-life", "Slice of Life"),
            Tag("sports", "Sports"),
            Tag("supernatural", "Supernatural"),
            Tag("thriller", "Thriller"),
            Tag("tragedy", "Tragedy"),
            Tag("yuri", "Yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.TriState(name)

    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
}
