package eu.kanade.tachiyomi.extension.id.pecintakomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class PecintaKomik : ParsedHttpSource() {

    override val name = "Pecinta Komik"

    override val baseUrl = "https://www.pecintakomik.net"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesRequest(page: Int):  Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl =  if(page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(builtUrl)
    }

    override fun latestUpdatesSelector() = ".releases:contains(update) + .listthumbx li"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("div.lx a.series")
        val imgurl = element.select("div.thumbx img").attr("src")
        manga.url = item.attr("href")
        manga.title = item.text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a.next"

    override fun popularMangaRequest(page: Int):  Request {
        val builtUrl =  if(page == 1) "$baseUrl/advanced-search/?order=popular" else "$baseUrl/advanced-search/page/$page/?order=popular"
        return GET(builtUrl)
    }

    override fun popularMangaSelector() = "div.listupd div.utao"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val imgurl = element.select("div.imgu img").attr("src")
        manga.url = element.select("div.imgu a").attr("href")
        manga.title = element.select("div.imgu a").attr("title")
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = if(page == 1) "$baseUrl/advanced-search/" else "$baseUrl/advanced-search/page/$page/"
        var types: String? = null
        fun requireNoType() = require(types == null) {
            "You cannot combine type with other filters!"
        }
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if(filter.state.isNotBlank()) {
                        requireNoType()
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is YearFilter -> {
                    if(filter.state.isNotBlank()) {
                        requireNoType()
                        url.addQueryParameter("yearx", filter.state)
                    }
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    if(status.isNotEmpty()) {
                        requireNoType()
                        url.addQueryParameter("status", status)
                    }
                }
                is TypeFilter -> {
                    if(filter.state != 0) {
                    types = if(page == 1) "$baseUrl/types/${filter.toUriPart()}/"  else "$baseUrl/types/${filter.toUriPart()}/page/$page/"
                    }
                }
                is OrderByFilter -> {
                    if(filter.state != 0) {
                        requireNoType()
                        url.addQueryParameter("order", filter.toUriPart())
                    }
                }
                is GenreList -> {
                    filter.state.forEach {
                        if (it.state) {
                            requireNoType()
                            url.addQueryParameter("genre[]", it.id)
                        }
                    }
                }
            }
        }
        return if (types != null) {
            GET("$types")
        } else {
            GET(url.build().toString(), headers)
        }
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
        val infoElement = document.select("div.infomanga")
        val author = infoElement.select("th:contains(Penulis) + td").text()
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = infoElement.select("th:contains(Status) + td").text()
        infoElement.select("th:contains(genre) + td a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.title = infoElement.select("h1 strong").text()
        manga.author = author
        manga.artist = author
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.description = Jsoup.parse(document.select("span.desc").html().substringAfter("<hr>").substringBefore("</p>")).text()
        manga.thumbnail_url = document.select("div.imgprop img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "h3:contains(Chapter) + ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("span.lchx a")
        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("span.dt").text()?.let {
            chapterParseDate(it)
        } ?: 0
        return chapter
    }

    private fun chapterParseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(date
            .replace("Mei", "May")
            .replace("Agu", "Aug")
            .replace("Okt", "Oct")
            .replace("Nop", "Nov")
            .replace("Des", "Dec")
        ).time
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
            if (url != "") {
                pages.add(Page(pages.size, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
            Filter.Header("Type filter cannot be combined."),
            TypeFilter(),
            Filter.Separator(),
            Filter.Header("Below you can combine filter."),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            OrderByFilter(),
            GenreList(getGenreList())
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter("Type", arrayOf(
            Pair("<select>", ""),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua")
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
            Tag("4-koma", "4-Koma"),
            Tag("4-koma-comedy", "4-Koma. Comedy"),
            Tag("action", "Action"),
            Tag("action-adventure", "Action. Adventure"),
            Tag("adult", "Adult"),
            Tag("adventure", "Adventure"),
            Tag("comed", "Comed"),
            Tag("comedy", "Comedy"),
            Tag("cooking", "Cooking"),
            Tag("demons", "Demons"),
            Tag("doujinshi", "Doujinshi"),
            Tag("drama", "Drama"),
            Tag("ecchi", "Ecchi"),
            Tag("echi", "Echi"),
            Tag("fantasy", "Fantasy"),
            Tag("game", "Game"),
            Tag("gender-bender", "Gender Bender"),
            Tag("gore", "Gore"),
            Tag("harem", "Harem"),
            Tag("historical", "Historical"),
            Tag("horor", "Horor"),
            Tag("horror", "Horror"),
            Tag("isekai", "Isekai"),
            Tag("josei", "Josei"),
            Tag("lolicon", "Lolicon"),
            Tag("magic", "Magic"),
            Tag("manhua", "Manhua"),
            Tag("martial-arts", "Martial Arts"),
            Tag("mature", "Mature"),
            Tag("mecha", "Mecha"),
            Tag("medical", "Medical"),
            Tag("military", "Military"),
            Tag("monster-girls", "Monster Girls"),
            Tag("music", "Music"),
            Tag("mystery", "Mystery"),
            Tag("oneshot", "Oneshot"),
            Tag("psychological", "Psychological"),
            Tag("romance", "Romance"),
            Tag("school", "School"),
            Tag("school-life", "School Life"),
            Tag("sci-fi", "Sci-Fi"),
            Tag("seinen", "Seinen"),
            Tag("shoujo", "Shoujo"),
            Tag("shoujo-ai", "Shoujo Ai"),
            Tag("shounen", "Shounen"),
            Tag("shounen-ai", "Shounen Ai"),
            Tag("si-fi", "Si-fi"),
            Tag("slice-of-life", "Slice of Life"),
            Tag("smut", "Smut"),
            Tag("sports", "Sports"),
            Tag("super-power", "Super Power"),
            Tag("supernatural", "Supernatural"),
            Tag("thriller", "Thriller"),
            Tag("tragedy", "Tragedy"),
            Tag("vampire", "Vampire"),
            Tag("webtoon", "Webtoon"),
            Tag("webtoons", "Webtoons"),
            Tag("yaoi", "Yaoi"),
            Tag("yuri", "Yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
}
