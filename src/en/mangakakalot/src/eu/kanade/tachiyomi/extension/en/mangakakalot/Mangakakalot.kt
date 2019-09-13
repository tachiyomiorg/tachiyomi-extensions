package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
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
import java.util.Calendar
import java.util.Locale

class Mangakakalot : ParsedHttpSource() {

    override val name = "Mangakakalot"

    override val baseUrl = "http://mangakakalot.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.truyen-list > div.list-truyen-item-wrap"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga_list?type=topview&category=all&state=all&page=$page")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga_list?type=latest&category=all&state=all&page=$page")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.page_select + a:not(.page_last)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga_list")!!.newBuilder()
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is StatusFilter -> {
                    url.addQueryParameter("state", filter.toUriPart())
                }
                is GenreFilter -> {
                    url.addQueryParameter("category", filter.toUriPart())
                }
            }
        }

        return if (query.isNotBlank()) {
            GET("$baseUrl/search/${normalizeSearchQuery(query)}?page=$page")
        } else GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = ".panel_story_list .story_item"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaSelector = if (document.select(searchMangaSelector()).isNotEmpty()) {
            searchMangaSelector()
        } else {
            popularMangaSelector()
        }
        val mangas = document.select(mangaSelector).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.manga-info-top").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()
        manga.author = infoElement.select("div.manga-info-top li").find { it.text().startsWith("Author") }?.text()?.substringAfter(") :")
        val status = infoElement.select("div.manga-info-top li").find { it.text().startsWith("Status") }?.text()?.substringAfter("Status :")
        manga.status = parseStatus(status)

        val genres = mutableListOf<String>()
        infoElement.select("div.manga-info-top li").find { it.text().startsWith("Genres :") }?.select("a")?.forEach { genres.add(it.text()) }
        manga.genre = genres.joinToString()
        manga.description = document.select("div#noidungm").text()
        manga.thumbnail_url = document.select("div.manga-info-pic").first().select("img").first().attr("src")
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

    override fun chapterListSelector() = "div.chapter-list div.row"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(element.select("span").last().text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        if ("ago" in date) {
            val value = date.split(' ')[0].toInt()

            if ("min" in date) {
                return Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
            }

            if ("hour" in date) {
                return Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
            }

            if ("day" in date) {
                return Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
            }
        }

        try {
            return SimpleDateFormat("MMM-dd-yy", Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
        }

        return 0L
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#vungdoc img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("No used")

    // Based on change_alias JS function from website
    private fun normalizeSearchQuery(query: String): String {
        var str = query.toLowerCase()
        str = str.replace("à|á|ạ|ả|ã|â|ầ|ấ|ậ|ẩ|ẫ|ă|ằ|ắ|ặ|ẳ|ẵ".toRegex(), "a")
        str = str.replace("è|é|ẹ|ẻ|ẽ|ê|ề|ế|ệ|ể|ễ".toRegex(), "e")
        str = str.replace("ì|í|ị|ỉ|ĩ".toRegex(), "i")
        str = str.replace("ò|ó|ọ|ỏ|õ|ô|ồ|ố|ộ|ổ|ỗ|ơ|ờ|ớ|ợ|ở|ỡ".toRegex(), "o")
        str = str.replace("ù|ú|ụ|ủ|ũ|ư|ừ|ứ|ự|ử|ữ".toRegex(), "u")
        str = str.replace("ỳ|ý|ỵ|ỷ|ỹ".toRegex(), "y")
        str = str.replace("đ".toRegex(), "d")
        str = str.replace("""!|@|%|\^|\*|\(|\)|\+|=|<|>|\?|/|,|\.|:|;|'| |"|&|#|\[|]|~|-|$|_""".toRegex(), "_")
        str = str.replace("_+_".toRegex(), "_")
        str = str.replace("""^_+|_+$""".toRegex(), "")
        return str
    }

    override fun getFilterList() = FilterList(
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            SortFilter(),
            StatusFilter(),
            GenreFilter()
    )

    private class SortFilter : UriPartFilter("Sort", arrayOf(
            Pair("latest", "Latest"),
            Pair("newest", "Newest"),
            Pair("topview", "Top read")
    ))

    private class StatusFilter : UriPartFilter("Status", arrayOf(
            Pair("all", "ALL"),
            Pair("completed", "Completed"),
            Pair("ongoing", "Ongoing"),
            Pair("drop", "Dropped")
    ))

    private class GenreFilter : UriPartFilter("Category", arrayOf(
            Pair("all", "ALL"),
            Pair("2", "Action"),
            Pair("3", "Adult"),
            Pair("4", "Adventure"),
            Pair("6", "Comedy"),
            Pair("7", "Cooking"),
            Pair("9", "Doujinshi"),
            Pair("10", "Drama"),
            Pair("11", "Ecchi"),
            Pair("12", "Fantasy"),
            Pair("13", "Gender bender"),
            Pair("14", "Harem"),
            Pair("15", "Historical"),
            Pair("16", "Horror"),
            Pair("45", "Isekai"),
            Pair("17", "Josei"),
            Pair("44", "Manhua"),
            Pair("43", "Manhwa"),
            Pair("19", "Martial arts"),
            Pair("20", "Mature"),
            Pair("21", "Mecha"),
            Pair("22", "Medical"),
            Pair("24", "Mystery"),
            Pair("25", "One shot"),
            Pair("26", "Psychological"),
            Pair("27", "Romance"),
            Pair("28", "School life"),
            Pair("29", "Sci fi"),
            Pair("30", "Seinen"),
            Pair("31", "Shoujo"),
            Pair("32", "Shoujo ai"),
            Pair("33", "Shounen"),
            Pair("34", "Shounen ai"),
            Pair("35", "Slice of life"),
            Pair("36", "Smut"),
            Pair("37", "Sports"),
            Pair("38", "Supernatural"),
            Pair("39", "Tragedy"),
            Pair("40", "Webtoons"),
            Pair("41", "Yaoi"),
            Pair("42", "Yuri")
    ))

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }
}
