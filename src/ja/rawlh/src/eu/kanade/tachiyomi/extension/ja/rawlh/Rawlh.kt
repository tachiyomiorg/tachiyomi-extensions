package eu.kanade.tachiyomi.extension.ja.rawlh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class Rawlh : ParsedHttpSource() {

    override val name = "RawLH"

    override val baseUrl = "http://lhscan.net"

    override val lang = "ja"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&sort=views&sort_type=DESC", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga-list.html?")!!.newBuilder().addQueryParameter("name", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Status -> {
                    val status = arrayOf("", "1", "2")[filter.state]
                    url.addQueryParameter("m_status", status)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
                is GenreList -> {

                    var genre = String()
                    var ungenre = String()

                    filter.state.forEach {
                        if (it.isIncluded()) genre += ",${it.name}"
                        if (it.isExcluded()) ungenre += ",${it.name}"
                    }
                    url.addQueryParameter("genre", genre)
                    url.addQueryParameter("ungenre", ungenre)
                }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&sort=last_update&sort_type=DESC")

    override fun popularMangaSelector() = "div.media"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 > a").first().let {
            manga.setUrlWithoutDomain("/" + it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a:contains(»)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.row").first()
        val genres = mutableListOf<String>()
        infoElement.select("li:contains(genre) a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }

        manga.author = infoElement.select("li:contains(author) a").text()
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("li:contains(status) a").first().text())

        manga.description = document.select("div.row:contains(description) > p").text()
        val imgUrl = document.select("img.thumbnail").first()?.attr("src")
        if (imgUrl!!.startsWith("app/")) {
            manga.thumbnail_url = "$baseUrl/$imgUrl"
        } else {
            manga.thumbnail_url = imgUrl
        }
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("On Going") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = " table.table.table-hover tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td a").first()
        val timeElement = element.select("td time").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/" + urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()
        return when {
            "hours ago" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "days ago" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "weeks ago" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "months ago" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "years ago" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("img.chapter-img").forEach {
            val dataSrc = it.attr("data-src")
            if (dataSrc != "") {
                pages.add(Page(pages.size, "", dataSrc))
            }else{
                val src = it.attr("src")
                pages.add(Page(pages.size, "", src))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Status : Filter.Select<String>("Status", arrayOf("Any", "Completed", "Ongoing"))
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
    private class Genre(name: String) : Filter.TriState(name)

    // TODO: Country
    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Group", "group"),
            Status(),
            GenreList(getGenreList())
    )

    // [...document.querySelectorAll("div.panel-body a")].map((el,i) => `Genre("${el.innerText.trim()}")`).join(',\n')
    //  on https://lhscan.net/search
    private fun getGenreList() = listOf(
            Genre("4-Koma"),
            Genre("Action"),
            Genre("Adult"),
            Genre("Adventure"),
            Genre("Isekai"),
            Genre("Comedy"),
            Genre("Comic"),
            Genre("Cooking"),
            Genre("Doujinshi"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Gender Bender"),
            Genre("Harem"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Josei"),
            Genre("Lolicon"),
            Genre("Manga"),
            Genre("Manhua"),
            Genre("Manhwa"),
            Genre("Martial Art"),
            Genre("Mature"),
            Genre("Mecha"),
            Genre("Medical"),
            Genre("Music"),
            Genre("Mystery"),
            Genre("One shot"),
            Genre("Psychological"),
            Genre("Romance"),
            Genre("School Life"),
            Genre("Sci-fi"),
            Genre("Seinen"),
            Genre("Shoujo"),
            Genre("Shojou Ai"),
            Genre("Shounen"),
            Genre("Shounen Ai"),
            Genre("Slice of Life"),
            Genre("Smut"),
            Genre("Sports"),
            Genre("Supernatural"),
            Genre("Tragedy"),
            Genre("Webtoon"),
            Genre("Yaoi"),
            Genre("Yuri")
    )
}
