package eu.kanade.tachiyomi.extension.all.lhreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import java.util.*

abstract class LHReader (
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64) Gecko/20100101 Firefox/69.0")
        add("Referer", baseUrl)
    }

    open val requestPath = "manga-list.html"



    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=views&sort_type=DESC", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/$requestPath?")!!.newBuilder()
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
        GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=last_update&sort_type=DESC")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        var hasNextPage = true

        document.select(popularMangaSelector()).forEach{ mangas.add(popularMangaFromElement(it)) }

        // check if there's a next page
        document.select(popularMangaNextPageSelector()).first().text().split(" ").let {
            val currentPage = it[1]
            val lastPage = it[3]
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

        element.select("h3 a").let {
            manga.setUrlWithoutDomain(it.attr("abs:href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").let{
            if (it.hasAttr("src")) {
                it.attr("abs:src")
            } else {
                it.attr("abs:data-original")
            }
        }

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // selects an element with text "x of y pages"
    override fun popularMangaNextPageSelector() = "div.col-lg-9 button.btn-info"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    //TODO better description selector
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.row").first()

        manga.author = infoElement.select("small a.btn.btn-xs.btn-info").first()?.text()
        manga.genre = infoElement.select("ul.manga-info li:nth-child(5) small a")?.text()?.replace(" ", ", ")
        manga.status = parseStatus(infoElement.select("a.btn.btn-xs.btn-success").first().text().toLowerCase(Locale.getDefault()))
        manga.description = document.select("div.row:has(h3:contains(description)) p, div.detail p")?.text()?.trim()
        manga.thumbnail_url = infoElement.select("img.thumbnail").attr("abs:src")

        return manga
    }

    //TODO other languages
    fun parseStatus(element: String): Int = when (element) {
        "completed", "chưa hoàn thành" -> SManga.COMPLETED
        "ongoing", "đã hoàn thành" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#list-chapters p, table.table tr"

    open val timeElementSelector = "time"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select("a").first().let{
            chapter.setUrlWithoutDomain(it.attr("abs:href"))
            chapter.name = it.text()
        }
        chapter.date_upload = parseChapterDate(element.select(timeElementSelector).text())

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        // languages: en, vi //TODO more languages
        return when (date.split(' ')[1].substringBefore("(").removeSuffix("s")) {
            "min", "minute", "phút" -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "hour", "giờ" -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "day", "ngày" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "week", "tuần" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "month", "tháng" -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "year", "năm" -> Calendar.getInstance().apply {
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

        document.select("img.chapter-img").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:data-src").let{ if (it.isNotEmpty()) it else img.attr("abs:src") }))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

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
