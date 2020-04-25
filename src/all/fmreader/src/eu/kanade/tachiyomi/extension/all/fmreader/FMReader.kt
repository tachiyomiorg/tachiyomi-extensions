package eu.kanade.tachiyomi.extension.all.fmreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.util.Calendar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * For sites based on the Flat-Manga CMS
 */
abstract class FMReader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64) Gecko/20100101 Firefox/75.0")
        add("Referer", baseUrl)
    }

    open val requestPath = "manga-list.html"

    open val popularSort = "sort=views"

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/$requestPath?listType=pagination&page=$page&$popularSort&sort_type=DESC", headers)

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
                    url.addQueryParameter("sort", when (filter.state?.index) {
                        0 -> "name"
                        1 -> "views"
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

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }

        // check if there's a next page
        val hasNextPage = (document.select(popularMangaNextPageSelector())?.first()?.text() ?: "").let {
            if (it.contains(Regex("""\w*\s\d*\s\w*\s\d*"""))) {
                it.split(" ").let { pageOf -> pageOf[1] != pageOf[3] } // current page not last page
            } else {
                it.isNotEmpty() // standard next page check
            }
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun popularMangaSelector() = "div.media"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                setUrlWithoutDomain(it.attr("abs:href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").let {
                if (it.hasAttr("src")) it.attr("abs:src") else it.attr("abs:data-original")
            }
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    /**
     * can select one of 2 different types of elements
     * one is an element with text "page x of y", must be the first element if it's part of a collection
     * the other choice is the standard "next page" element (but most FMReader sources don't have this one)
     */
    override fun popularMangaNextPageSelector() = "div.col-lg-9 button.btn-info"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.row").first()

        return SManga.create().apply {
            author = infoElement.select("li a.btn-info").text()
            genre = infoElement.select("li a.btn-danger").joinToString { it.text() }
            status = parseStatus(infoElement.select("li a.btn-success").first()?.text())
            description = document.select("div.row ~ div.row p").text().trim()
            thumbnail_url = infoElement.select("img.thumbnail").attr("abs:src")
        }
    }

    // languages: en, vi, tr
    fun parseStatus(status: String?): Int {
        val completedWords = setOf("completed", "complete", "incomplete", "đã hoàn thành", "tamamlandı", "hoàn thành")
        val ongoingWords = setOf("ongoing", "on going", "updating", "chưa hoàn thành", "đang cập nhật", "devam ediyor", "Đang tiến hành")
        return when {
            status == null -> SManga.UNKNOWN
            completedWords.any { it.equals(status, ignoreCase = true) } -> SManga.COMPLETED
            ongoingWords.any { it.equals(status, ignoreCase = true) } -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "div#list-chapters p, table.table tr"

    open val chapterUrlSelector = "a"

    open val chapterTimeSelector = "time"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select(chapterUrlSelector).first().let {
                setUrlWithoutDomain(it.attr("abs:href"))
                name = it.text()
            }
            date_upload = element.select(chapterTimeSelector).let { if (it.hasText()) parseChapterDate(it.text()) else 0 }
        }
    }

    // gets the number from "1 day ago"
    open val dateValueIndex = 0

    // gets the unit of time (day, week hour) from "1 day ago"
    open val dateWordIndex = 1

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val dateWord = date.split(' ')[dateWordIndex].let {
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

    open val pageListImageSelector = "img.chapter-img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListImageSelector).mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src").let { if (it.isNotEmpty()) it else img.attr("abs:src") })
        }
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
