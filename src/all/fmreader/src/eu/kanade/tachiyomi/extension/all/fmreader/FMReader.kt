package eu.kanade.tachiyomi.extension.all.fmreader

// For sites based on the Flat-Manga CMS

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import java.util.*

abstract class FMReader (
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

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
                is SortBy -> {
                    url.addQueryParameter("sort", when {
                        filter.state?.index == 0 -> "name"
                        filter.state?.index == 1 -> "views"
                        else -> "last_update"
                    })
                    if (filter.state?.ascending == true)
                        url.addQueryParameter("sort_type", "ASC")
                }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=last_update&sort_type=DESC", headers)

    // for sources that don't have the "page x of y" element
    fun defaultMangaParse(response: Response): MangasPage = super.popularMangaParse(response)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        var hasNextPage = true

        document.select(popularMangaSelector()).map{ mangas.add(popularMangaFromElement(it)) }

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

    // selects an element with text "x of y pages", must be first element if multiple elements are selected
    override fun popularMangaNextPageSelector() = "div.col-lg-9 button.btn-info"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.row").first()

        manga.author = infoElement.select("li a.btn-info").text()
        manga.genre = infoElement.select("li a.btn-danger").joinToString { it.text() }
        manga.status = parseStatus(infoElement.select("li a.btn-success").first().text().toLowerCase())
        manga.description = document.select("div.row ~ div.row p").text().trim()
        manga.thumbnail_url = infoElement.select("img.thumbnail").attr("abs:src")

        return manga
    }

    // languages: en, vi, tr
    fun parseStatus(element: String): Int = when (element) {
        "completed", "complete", "incomplete", "đã hoàn thành", "tamamlandı" -> SManga.COMPLETED
        "ongoing", "on going", "updating", "chưa hoàn thành", "đang cập nhật", "devam ediyor" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#list-chapters p, table.table tr"

    open val chapterUrlSelector = "a"

    open val chapterTimeSelector = "time"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select(chapterUrlSelector).first().let{
            chapter.setUrlWithoutDomain(it.attr("abs:href"))
            chapter.name = it.text()
        }
        chapter.date_upload = element.select(chapterTimeSelector).let{ if(it.hasText()) parseChapterDate(it.text()) else 0 }

        return chapter
    }

    // gets the number from "1 day ago"
    open val dateValueIndex = 0

    // gets the unit of time (day, week hour) from "1 day ago"
    open val dateWordIndex = 1

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val dateWord = date.split(' ')[dateWordIndex].let{
            if (it.contains("(")) {
                it.substringBefore("(")
            } else {
                it.substringBefore("s")
            }
        }

        // languages: en, vi, es, tr
        return when (dateWord) {
            "min", "minute", "phút", "minuto", "dakika" -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "hour", "giờ", "hora", "saat" -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "day", "ngày", "día", "gün" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "week", "tuần", "semana", "hafta" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "month", "tháng", "mes", "ay" -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "year", "năm", "año", "yıl" -> Calendar.getInstance().apply {
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
    class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
    class Genre(name: String, val id: String = name.replace(' ', '+')) : Filter.TriState(name)
    private class SortBy : Filter.Sort("Sorted By", arrayOf("A-Z", "Most vỉews", "Last updated"), Selection(1, false))

    // TODO: Country (leftover from original LHTranslation)
    override fun getFilterList() = FilterList(
        TextField("Author", "author"),
        TextField("Group", "group"),
        Status(),
        SortBy(),
        GenreList(getGenreList())
    )

    // [...document.querySelectorAll("div.panel-body a")].map((el,i) => `Genre("${el.innerText.trim()}")`).join(',\n')
    //  on https://lhtranslation.net/search
    open fun getGenreList() = listOf(
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

    // from manhwa18.com/search, removed a few that didn't return results/wouldn't be terribly useful
    fun getAdultGenreList() = listOf(
        Genre("18"),
        Genre("Action"),
        Genre("Adult"),
        Genre("Adventure"),
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
        Genre("Magic"),
        Genre("Manhua"),
        Genre("Manhwa"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Mystery"),
        Genre("Oneshot"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci-fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of life"),
        Genre("Smut"),
        Genre("Soft Yaoi"),
        Genre("Soft Yuri"),
        Genre("Sports"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("VnComic"),
        Genre("Webtoon")
    )

    // taken from readcomiconline.org/search
    fun getComicsGenreList() = listOf(
        Genre("Action"),
        Genre("Adventure"),
        Genre("Anthology"),
        Genre("Anthropomorphic"),
        Genre("Biography"),
        Genre("Children"),
        Genre("Comedy"),
        Genre("Crime"),
        Genre("Drama"),
        Genre("Family"),
        Genre("Fantasy"),
        Genre("Fighting"),
        Genre("GraphicNovels"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("LeadingLadies"),
        Genre("LGBTQ"),
        Genre("Literature"),
        Genre("Manga"),
        Genre("MartialArts"),
        Genre("Mature"),
        Genre("Military"),
        Genre("Mystery"),
        Genre("Mythology"),
        Genre("Personal"),
        Genre("Political"),
        Genre("Post-Apocalyptic"),
        Genre("Psychological"),
        Genre("Pulp"),
        Genre("Religious"),
        Genre("Robots"),
        Genre("Romance"),
        Genre("Schoollife"),
        Genre("Sci-Fi"),
        Genre("Sliceoflife"),
        Genre("Sport"),
        Genre("Spy"),
        Genre("Superhero"),
        Genre("Supernatural"),
        Genre("Suspense"),
        Genre("Thriller"),
        Genre("Vampires"),
        Genre("VideoGames"),
        Genre("War"),
        Genre("Western"),
        Genre("Zombies")
    )
}
