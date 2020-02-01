package eu.kanade.tachiyomi.extension.en.mangahub

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.github.salomonbrys.kotson.keys
import com.google.gson.Gson
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.select("span._8Qtbo").text()
        chapter.date_upload = element.select("small.UovLc").first()?.text()?.let { parseChapterDate(it) } ?: 0

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var parsedDate = 0L
        when {
            "just now" in date || "less than an hour" in date -> {
                parsedDate = now.timeInMillis
            }
            // parses: "1 hour ago" and "2 hours ago"
            "hour" in date -> {
                val hours = date.replaceAfter(" ", "").trim().toInt()
                parsedDate = now.apply { add(Calendar.HOUR, -hours) }.timeInMillis
            }
            // parses: "Yesterday" and "2 days ago"
            "day" in date -> {
                val days = date.replace("days ago", "").trim().toIntOrNull() ?: 1
                parsedDate = now.apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
            }
            // parses: "2 weeks ago"
            "weeks" in date -> {
                val weeks = date.replace("weeks ago", "").trim().toInt()
                parsedDate = now.apply { add(Calendar.WEEK_OF_YEAR, -weeks) }.timeInMillis
            }
            // parses: "12-20-2019" and defaults everything that wasn't taken into account to 0
            else -> {
                try {
                    parsedDate = SimpleDateFormat("MM-dd-yyyy", Locale.US).parse(date).time
                } catch (e: ParseException) { /*nothing to do, parsedDate is initialized with 0L*/ }
            }
        }
        return parsedDate
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val jsonHeaders = headers.newBuilder().add("Content-Type", "application/json").build()

        val slug = chapter.url.substringAfter("chapter/").substringBefore("/")
        val number = chapter.url.substringAfter("chapter-").removeSuffix("/")
        val body = RequestBody.create(null, "{\"query\":\"{chapter(x:m01,slug:\\\"$slug\\\",number:$number){id,title,mangaID,number,slug,date,pages,noAd,manga{id,title,slug,mainSlug,author,isWebtoon,isYaoi,isPorn,isSoftPorn,unauthFile,isLicensed}}}\"}")

        return POST("https://api.mghubcdn.com/graphql", jsonHeaders, body)
    }

    private val gson = Gson()

    override fun pageListParse(response: Response): List<Page> {
        val cdn = "https://img.mghubcdn.com/file/imghub"

        return gson.fromJson<JsonObject>(response.body()!!.string())["data"]["chapter"]["pages"].string
            .removeSurrounding("\"").replace("\\", "")
            .let { cleaned ->
                val jsonObject = gson.fromJson<JsonObject>(cleaned)
                jsonObject.keys().map { key -> jsonObject[key].string }
            }
            .mapIndexed { i, tail -> Page(i, "", "$cdn/$tail") }
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not used")

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
