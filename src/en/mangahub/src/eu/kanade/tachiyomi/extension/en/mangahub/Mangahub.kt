package eu.kanade.tachiyomi.extension.en.mangahub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class Mangahub : ParsedHttpSource() {

    override val name = "Mangahub"

    override val baseUrl = "https://www.mangahub.io"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = "#mangalist div.media-manga.media"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/page/$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/updates/page/$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val titleElement = element.select(".media-heading > a").first()
        manga.title = titleElement.text()
        manga.setUrlWithoutDomain(URL(titleElement.attr("href")).path)
        manga.thumbnail_url = element.select("img.manga-thumb.list-item-thumb")
                ?.first()?.attr("src")

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "ul.pager li.next > a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h1._3xnDj").first().text()
        manga.author = document.select("._3QCtP > div:nth-child(2) > div:nth-child(1) > span:nth-child(2)")?.first()?.text()
        manga.artist = document.select("._3QCtP > div:nth-child(2) > div:nth-child(2) > span:nth-child(2)")?.first()?.text()
        manga.genre = document.select("._3Czbn a")?.joinToString { it.text() }
        manga.description = document.select("div#noanim-content-tab-pane-99 p.ZyMp7")?.first()?.text()
        manga.thumbnail_url = document.select("img.img-responsive")?.first()
                ?.attr("src")

        document.select("._3QCtP > div:nth-child(2) > div:nth-child(3) > span:nth-child(2)")?.first()?.text()?.also { statusText ->
            when {
                statusText.contains("ongoing", true) -> manga.status = SManga.ONGOING
                statusText.contains("completed", true) -> manga.status = SManga.COMPLETED
                else -> manga.status = SManga.UNKNOWN
            }
        }

        return manga
    }

    override fun chapterListSelector() = ".tab-content .tab-pane li.list-group-item > a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(URL(element.attr("href")).path)

        val titleHeader = element.select(".text-secondary").first()
        val number = titleHeader.select("._3D1SJ").first().text()
        val title = titleHeader.select("._2IG5P").first().text()

        chapter.name = "$number $title"
        chapter.date_upload = element.select("small.UovLc").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return when {
            "hours" in date || "just now" in date -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            "days" in date -> {
                val days = date.replace("days ago", "").trim().toInt()
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -days)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            "weeks" in date -> {
                val weeks = date.replace("weeks ago", "").trim().toInt()
                Calendar.getInstance().apply {
                    add(Calendar.WEEK_OF_YEAR, -weeks)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            else -> {
                try {
                    SimpleDateFormat("MM-dd-yyyy", Locale.US).parse(date).time
                } catch (e: ParseException) {
                    0L
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageList = mutableListOf<Page>()

        val page = document.select("div#mangareader img.PB0mN").first()
        val pageUrl = page.attr("src")
        val extension = pageUrl.split(".").last()
        val pageRoot = pageUrl.replaceAfterLast("/", "")
        val numPages = page.nextElementSibling().text().split("/").last().toInt()

        for (i in 1..numPages) {
            pageList.add(Page(i, "", "$pageRoot$i.$extension"))
        }

        return pageList
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    //https://mangahub.io/search/page/1?q=a&order=POPULAR&genre=all
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search/page/$page")?.newBuilder()!!.addQueryParameter("q", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val order = filter.values[filter.state]
                    url.addQueryParameter("order", order.key)
                }
                is GenreList -> {
                    val genre = filter.values[filter.state]
                    url.addQueryParameter("genre", genre.key)
                }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div#mangalist"

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    private class Genre(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders,0)
    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Genres", genres, 0)

    override fun getFilterList() = FilterList(
            OrderBy(orderBy),
            GenreList(genres)
    )

    private val orderBy = arrayOf(
            Order("Popular", "POPULAR"),
            Order("Updates", "LATEST"),
            Order("A-Z", "ALPHABET"),
            Order("New", "NEW"),
            Order("Completed", "COMPLETED")
    )

    private val genres = arrayOf(
        Genre("All Genres", "all"),
        Genre("[no chapters]", "no-chapters"),
        Genre("4-Koma", "4-koma"),
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Award Winning", "award-winning"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Food", "food"),
        Genre("Game", "game"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School life", "school-life"),
        Genre("Sci fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shoujoai", "shoujoai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Shounenai", "shounenai"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Space", "space"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Superhero", "superhero"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Wuxia", "wuxia"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}
