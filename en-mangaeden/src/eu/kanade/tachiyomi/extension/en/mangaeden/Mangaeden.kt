package eu.kanade.tachiyomi.extension.en.mangaeden

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Mangaeden : ParsedHttpSource() {

    override val name = "Mangaeden"

    override val baseUrl = "http://www.mangaeden.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/en/en-directory/?order=3&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/en/en-directory/?order=1&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/en/en-directory/").newBuilder().addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is StatusList -> filter.state
                        .filter { it.state }
                        .map { it.id.toString() }
                        .forEach { url.addQueryParameter("status", it) }
                is Types -> filter.state
                        .filter { it.state }
                        .map { it.id.toString() }
                        .forEach { url.addQueryParameter("type", it) }
                is GenreList -> filter.state
                        .filter { !it.isIgnored() }
                        .forEach { genre -> url.addQueryParameter(if (genre.isIncluded()) "categoriesInc" else "categoriesExcl", genre.id) }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is OrderBy -> filter.state?.let {
                    val sortId = it.index
                    url.addQueryParameter("order", if (it.ascending) "-$sortId" else "$sortId")
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table#mangaList > tbody > tr:has(td:gt(1))"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("td > a").first()?.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
    }

    override fun searchMangaNextPageSelector() = "a:has(span.next)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infos = document.select("div.rightbox")

        author = infos.select("a[href^=/en/en-directory/?author]").first()?.text()
        artist = infos.select("a[href^=/en/en-directory/?artist]").first()?.text()
        genre = infos.select("a[href^=/en/en-directory/?categoriesInc]").map { it.text() }.joinToString()
        description = document.select("h2#mangaDescription").text()
        status = parseStatus(infos.select("h4:containsOwn(Status)").first()?.nextSibling().toString())
        thumbnail_url = infos.select("div.mangaImage2 > img").first()?.attr("src").let { "http:$it" }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#leftContent > table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.select("a[href^=/en/en-manga/]").first()

        setUrlWithoutDomain(a.attr("href"))
        name = a?.select("b")?.first()?.text().orEmpty()
        date_upload = element.select("td.chapterDate").first()?.text()?.let { parseChapterDate(it.trim()) } ?: 0L
    }

    private fun parseChapterDate(date: String): Long =
            if ("Today" in date) {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else if ("Yesterday" in date) {
                Calendar.getInstance().apply {
                    add(Calendar.DATE, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else try {
                SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).parse(date).time
            } catch (e: ParseException) {
                0L
            }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("option[value^=/en/en-manga/]").forEach {
            add(Page(size, "$baseUrl${it.attr("value")}"))
        }
    }

    override fun imageUrlParse(document: Document): String = document.select("a#nextA.next > img").first()?.attr("src").let { "http:$it" }

    private class NamedId(name: String, val id: Int) : Filter.CheckBox(name)
    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class OrderBy : Filter.Sort("Order by", arrayOf("Manga title", "Views", "Chapters", "Latest chapter"),
            Filter.Sort.Selection(1, false))

    private class StatusList(statuses: List<NamedId>) : Filter.Group<NamedId>("Stato", statuses)
    private class Types(types: List<NamedId>) : Filter.Group<NamedId>("Tipo", types)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Artist", "artist"),
            Types(types()),
            StatusList(statuses()),
            OrderBy(),
            GenreList(genres())
    )

    private fun types() = listOf(
            NamedId("Japanese Manga", 0),
            NamedId("Korean Manhwa", 1),
            NamedId("Chinese Manhua", 2),
            NamedId("Comic", 3),
            NamedId("Doujinshi", 4)
    )

    private fun statuses() = listOf(
            NamedId("Ongoing", 1),
            NamedId("Completed", 2),
            NamedId("Suspended", 0)
    )

    private fun genres() = listOf(
            Genre("Action", "4e70e91bc092255ef70016f8"),
            Genre("Adult", "4e70e92fc092255ef7001b94"),
            Genre("Adventure", "4e70e918c092255ef700168e"),
            Genre("Comedy", "4e70e918c092255ef7001675"),
            Genre("Doujinshi", "4e70e928c092255ef7001a0a"),
            Genre("Drama", "4e70e918c092255ef7001693"),
            Genre("Ecchi", "4e70e91ec092255ef700175e"),
            Genre("Fantasy", "4e70e918c092255ef7001676"),
            Genre("Gender Bender", "4e70e921c092255ef700184b"),
            Genre("Harem", "4e70e91fc092255ef7001783"),
            Genre("Historical", "4e70e91ac092255ef70016d8"),
            Genre("Horror", "4e70e919c092255ef70016a8"),
            Genre("Josei", "4e70e920c092255ef70017de"),
            Genre("Martial Arts", "4e70e923c092255ef70018d0"),
            Genre("Mature", "4e70e91bc092255ef7001705"),
            Genre("Mecha", "4e70e922c092255ef7001877"),
            Genre("Mystery", "4e70e918c092255ef7001681"),
            Genre("One Shot", "4e70e91dc092255ef7001747"),
            Genre("Psychological", "4e70e919c092255ef70016a9"),
            Genre("Romance", "4e70e918c092255ef7001677"),
            Genre("School Life", "4e70e918c092255ef7001688"),
            Genre("Sci-fi", "4e70e91bc092255ef7001706"),
            Genre("Seinen", "4e70e918c092255ef700168b"),
            Genre("Shoujo", "4e70e918c092255ef7001667"),
            Genre("Shounen", "4e70e918c092255ef700166f"),
            Genre("Slice of Life", "4e70e918c092255ef700167e"),
            Genre("Smut", "4e70e922c092255ef700185a"),
            Genre("Sports", "4e70e91dc092255ef700172e"),
            Genre("Supernatural", "4e70e918c092255ef700166a"),
            Genre("Tragedy", "4e70e918c092255ef7001672"),
            Genre("Webtoons", "4e70ea70c092255ef7006d9c"),
            Genre("Yaoi", "4e70e91ac092255ef70016e5"),
            Genre("Yuri", "4e70e92ac092255ef7001a57")
    )
}