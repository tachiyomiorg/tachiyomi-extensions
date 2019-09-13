package eu.kanade.tachiyomi.extension.en.lhtranslation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class LHTranslation : ParsedHttpSource() {
    // Ps. LHTranslation is really similar to rawLH
    override val name = "LHTranslation"

    override val baseUrl = "https://lhtranslation.net"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&sort=views&sort_type=DESC", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga-list.html?")!!.newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("page", page.toString())
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

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        var hasNextPage = true

        document.select(popularMangaSelector()).forEach{ mangas.add(popularMangaFromElement(it)) }

        // check if there's a next page
        document.select(popularMangaNextPageSelector()).first().text().let {
            val currentPage = it.substringAfter("Page ").substringBefore(" ")
            val lastPage = it.substringAfterLast(" ")
            if (currentPage == lastPage) hasNextPage = false
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

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

    override fun popularMangaNextPageSelector() = "div.col-lg-9 button.btn-info"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.row").first()
        val genres = infoElement.select("ul.manga-info li:nth-child(5) small a")?.map {
            it.text()
        }
        manga.author = infoElement.select("small a.btn.btn-xs.btn-info").first()?.text()
        manga.genre = genres?.joinToString(", ")
        manga.status = parseStatus(infoElement.select("a.btn.btn-xs.btn-success").first().text())

        manga.description = document.select("div.row > p")?.text()?.trim()
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
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".list-chapters .list-wrap p"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".titleLink a").first()
        val timeElement = element.select(".pubDate time").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/" + urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()
        return when {
            "min(s) ago" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "hour(s) ago" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "day(s) ago" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "week(s) ago" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "month(s) ago" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "year(s) ago" in date -> Calendar.getInstance().apply {
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
        document.select(".chapter-img").forEach {
            val url = it.attr("src")
            if (url != "") {
                pages.add(Page(pages.size, "", url))
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
    private class Genre(name: String, val id: String = name.replace(' ', '+')) : Filter.TriState(name)

    // TODO: Country
    override fun getFilterList() = FilterList(
            TextField("Author", "author"),
            TextField("Group", "group"),
            Status(),
            GenreList(getGenreList())
    )

    // [...document.querySelectorAll("div.panel-body a")].map((el,i) => `Genre("${el.innerText.trim()}")`).join(',\n')
    //  on https://lhtranslation.net/search
    private fun getGenreList() = listOf(
            Genre("Action"),
            Genre("18+"),
            Genre("Adult"),
            Genre("Anime"),
            Genre("Comedy"),
            Genre("Comic"),
            Genre("Doujinshi"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Gender Bender"),
            Genre("Harem"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Josei"),
            Genre("Live action"),
            Genre("Manhua"),
            Genre("Manhwa"),
            Genre("Martial Art"),
            Genre("Mature"),
            Genre("Mecha"),
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
            Genre("Adventure"),
            Genre("Yaoi")
    )
}
