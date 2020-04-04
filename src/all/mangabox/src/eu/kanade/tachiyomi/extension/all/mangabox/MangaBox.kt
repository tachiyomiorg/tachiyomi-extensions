package eu.kanade.tachiyomi.extension.all.mangabox

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// Based off of Mangakakalot 1.2.8

abstract class MangaBox (
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateformat: SimpleDateFormat = SimpleDateFormat("MMM-dd-yy", Locale.ENGLISH)
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    open val popularUrlPath = "manga_list?type=topview&category=all&state=all&page="

    open val latestUrlPath = "manga_list?type=latest&category=all&state=all&page="

    open val simpleQueryPath = "search/"

    override fun popularMangaSelector() = "div.truyen-list > div.list-truyen-item-wrap"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$popularUrlPath$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$latestUrlPath$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").first().let {
                url = it.attr("abs:href").substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
                title = it.text()
            }
            thumbnail_url = element.select("img").first().attr("abs:src")
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.group_page, div.group-page a:not([href]) + a:not(:contains(Last))"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/$simpleQueryPath${normalizeSearchQuery(query)}?page=$page", headers)
        } else {
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
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = ".panel_story_list .story_item"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    open val mangaDetailsMainSelector = "div.manga-info-top, div.panel-story-info"

    open val thumbnailSelector = "div.manga-info-pic img, span.info-image img"

    open val descriptionSelector = "div#noidungm, div#panel-story-info-description"

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(mangaDetailsMainSelector)

        return SManga.create().apply {
            title = infoElement.select("h1, h2").first().text()
            author = infoElement.select("li:contains(author) a, td:containsOwn(author) + td").text()
            status = parseStatus(infoElement.select("li:contains(status), td:containsOwn(status) + td").text())
            genre = infoElement.select("div.manga-info-top li:contains(genres)").let { kakalotE ->
                if (kakalotE.isNotEmpty()) {
                    kakalotE.text().substringAfter(": ")
                } else {
                    // Nelo
                    infoElement.select("td:containsOwn(genres) + td a").joinToString { it.text() }
                }
            }
            description = document.select(descriptionSelector)?.first()?.ownText()
                ?.replace("""^$title summary:\s""".toRegex(), "")
                ?.replace("""<\s*br\s*\/?>""".toRegex(), "\n")
                ?.replace("<[^>]*>".toRegex(), "")
            thumbnail_url = document.select(thumbnailSelector).attr("abs:src")
        }
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

    override fun chapterListSelector() = "div.chapter-list div.row, ul.row-content-chapter li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                url = it.attr("abs:href").substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
                name = it.text()
            }
            date_upload = parseChapterDate(element.select("span").last().text(), element.ownerDocument().location()) ?: 0
        }
    }

    private fun parseChapterDate(date: String, url: String): Long? {
        return if ("ago" in date) {
            val value = date.split(' ')[0].toIntOrNull()
            val cal = Calendar.getInstance()
            when {
                value != null && "min" in date -> cal.apply { add(Calendar.MINUTE, value * -1) }
                value != null && "hour" in date -> cal.apply { add(Calendar.HOUR_OF_DAY, value * -1) }
                value != null && "day" in date -> cal.apply { add(Calendar.DATE, value * -1) }
                else -> null
            }?.timeInMillis
        } else {
            try {
                if (url.contains("manganelo")) {
                    // Nelo's date format
                    SimpleDateFormat("MMM dd,yy", Locale.ENGLISH).parse(date)
                } else {
                    dateformat.parse(date)
                }
            } catch (e: ParseException) {
                null
            }?.time
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    open val pageListSelector = "div#vungdoc img, div.container-chapter-reader img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector).mapIndexed { i, element ->
            val url = element.attr("abs:src").let { src ->
                if (src.startsWith("https://convert_image_digi.mgicdn.com")) {
                    "https://images.weserv.nl/?url=" + src.substringAfter("//")
                } else {
                    src
                }
            }
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Based on change_alias JS function from Mangakakalot's website
    open fun normalizeSearchQuery(query: String): String {
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

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }
}
