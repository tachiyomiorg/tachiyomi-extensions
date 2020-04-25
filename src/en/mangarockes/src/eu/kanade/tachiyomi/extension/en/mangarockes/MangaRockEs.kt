package eu.kanade.tachiyomi.extension.en.mangarockes

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.JsonArray
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import kotlin.experimental.and
import kotlin.experimental.xor


class MangaRockEs : ParsedHttpSource() {

    override val name = "MangaRock.es"

    override val baseUrl = "https://mangarock.es"

    override val lang = "en"

    override val supportsLatest = true

    // Handles the page decoding
    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor(fun(chain): Response {
        val url = chain.request().url().toString()
        val response = chain.proceed(chain.request())
        if (!url.endsWith(".mri")) return response

        val decoded: ByteArray = decodeMri(response)
        val mediaType = MediaType.parse("image/webp")
        val rb = ResponseBody.create(mediaType, decoded)
        return response.newBuilder().body(rb).build()
    }).build()

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/$page?sort=rank", headers)

    override fun popularMangaSelector() = "div.col-five"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.page-link:contains(next)"

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/latest/$page?sort=date", headers)

    override fun latestUpdatesSelector() = "div.product-item-detail"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search/${query.replace(" ", "+")}/$page", headers)
        } else {
            val url = HttpUrl.parse("$baseUrl/manga" + if (page > 1) "/$page" else "")!!.newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())
                    is RankFilter -> url.addQueryParameter("rank", filter.toUriPart())
                    is SortBy -> url.addQueryParameter("sort", filter.toUriPart())
                    is GenreList -> {
                        val genres = filter.state
                            .filter { it.state }
                            .joinToString(".") { it.uriPart }
                        url.addQueryParameter("genres", genres)
                    }
                }
            }
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        return SManga.create().apply {
            document.select("div.block-info-manga").let { info ->
                thumbnail_url = info.select("div.thumb_inner").attr("style")
                    .substringAfter("'").substringBefore("'")
                title = info.select("h1").text()
                author = info.select("div.author_item").text()
                status = info.select("div.status_chapter_item").text().substringBefore(" ").let {
                    when {
                        it.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
                        it.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }
            genre = document.select("div.tags a").joinToString { it.text() }
            description = document.select("div.full.summary p").filterNot { it.text().isNullOrEmpty() }.joinToString("\n")
        }
    }

    // Chapters

    override fun chapterListSelector(): String = "tbody[data-test=chapter-table] tr"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("td").lastOrNull()?.text()?.let { date ->
                if (date.contains("ago", ignoreCase = true)) {
                    val trimmedDate = date.substringBefore(" ago").removeSuffix("s").split(" ")

                    val calendar = Calendar.getInstance()
                    when (trimmedDate[1]) {
                        "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
                        "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
                        "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
                        "second" -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }
                    }

                    calendar.timeInMillis
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.US).parse(date).time
                }
            } ?: 0
        }
    }

    // Pages

    private val gson by lazy { Gson() }

    override fun pageListParse(response: Response): List<Page> {
        val array = Regex("""mangaData = (\[.*]);""", RegexOption.IGNORE_CASE)
            .find(response.body()!!.string())?.groupValues?.get(1) ?: throw Exception ("mangaData array not found")
        return gson.fromJson<JsonArray>(array).mapIndexed { i, jsonElement ->
            Page(i, "", jsonElement.asJsonObject["url"].asString)
        }
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("This method should not be called!")

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    // Filters

    // See drawWebpToCanvas function in the site's client.js file
    // Extracted code: https://jsfiddle.net/6h2sLcs4/30/
    private fun decodeMri(response: Response): ByteArray {
        val data = response.body()!!.bytes()

        // Decode file if it starts with "E" (space when XOR-ed later)
        if (data[0] != 69.toByte()) return data

        // Reconstruct WEBP header
        // Doc: https://developers.google.com/speed/webp/docs/riff_container#webp_file_header
        val buffer = ByteArray(data.size + 15)
        val size = data.size + 7
        buffer[0] = 82  // R
        buffer[1] = 73  // I
        buffer[2] = 70  // F
        buffer[3] = 70  // F
        buffer[4] = (255.toByte() and size.toByte())
        buffer[5] = (size ushr 8).toByte() and 255.toByte()
        buffer[6] = (size ushr 16).toByte() and 255.toByte()
        buffer[7] = (size ushr 24).toByte() and 255.toByte()
        buffer[8] = 87  // W
        buffer[9] = 69  // E
        buffer[10] = 66 // B
        buffer[11] = 80 // P
        buffer[12] = 86 // V
        buffer[13] = 80 // P
        buffer[14] = 56 // 8

        // Decrypt file content using XOR cipher with 101 as the key
        val cipherKey = 101.toByte()
        for (r in data.indices) {
            buffer[r + 15] = cipherKey xor data[r]
        }

        return buffer
    }

    // Filters

    private class StatusFilter : UriPartFilter("Status", arrayOf(
        Pair("All", "all"),
        Pair("Completed", "completed"),
        Pair("Ongoing", "ongoing")
    ))

    private class RankFilter : UriPartFilter("Rank", arrayOf(
            Pair("All", "all"),
            Pair("1 - 999", "1-999"),
            Pair("1k - 2k", "1000-2000"),
            Pair("2k - 3k", "2000-3000"),
            Pair("3k - 4k", "3000-4000"),
            Pair("4k - 5k", "4000-5000"),
            Pair("5k - 6k", "5000-6000"),
            Pair("6k - 7k", "6000-7000"),
            Pair("7k - 8k", "7000-8000"),
            Pair("8k - 9k", "8000-9000"),
            Pair("9k - 19k", "9000-10000"),
            Pair("10k - 11k", "10000-11000")
    ))

    private class SortBy : UriPartFilter("Sort by", arrayOf(
            Pair("Name", "name"),
            Pair("Rank", "rank")
    ))

    private class Genre(name: String, val uriPart: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            // Search and filter don't work at the same time
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            StatusFilter(),
            RankFilter(),
            SortBy(),
            GenreList(getGenreList())
    )

    // [...document.querySelectorAll('._2DMqI .mdl-checkbox')].map(n => `Genre("${n.querySelector('.mdl-checkbox__label').innerText}", "${n.querySelector('input').dataset.oid}")`).sort().join(',\n')
    // on https://mangarock.com/manga
    private fun getGenreList() = listOf(
            Genre("4-koma", "4-koma"),
            Genre("Action", "action"),
            Genre("Adult", "adult"),
            Genre("Adventure", "adventure"),
            Genre("Comedy", "comedy"),
            Genre("Demons", "demons"),
            Genre("Doujinshi", "doujinshi"),
            Genre("Drama", "drama"),
            Genre("Ecchi", "ecchi"),
            Genre("Fantasy", "fantasy"),
            Genre("Gender Bender", "gender-bender"),
            Genre("Harem", "harem"),
            Genre("Historical", "historical"),
            Genre("Horror", "horror"),
            Genre("Isekai", "isekai"),
            Genre("Josei", "josei"),
            Genre("Kids", "kids"),
            Genre("Magic", "magic"),
            Genre("Martial Arts", "martial-arts"),
            Genre("Mature", "mature"),
            Genre("Mecha", "mecha"),
            Genre("Military", "military"),
            Genre("Music", "music"),
            Genre("Mystery", "mystery"),
            Genre("One Shot", "one-shot"),
            Genre("Parody", "parody"),
            Genre("Police", "police"),
            Genre("Psychological", "psychological"),
            Genre("Romance", "romance"),
            Genre("School Life", "school-life"),
            Genre("Sci-Fi", "sci-fi"),
            Genre("Seinen", "seinen"),
            Genre("Shoujo Ai", "shoujo-ai"),
            Genre("Shoujo", "shoujo"),
            Genre("Shounen Ai", "shounen-ai"),
            Genre("Shounen", "shounen"),
            Genre("Slice of Life", "slice-of-life"),
            Genre("Smut", "smut"),
            Genre("Space", "space"),
            Genre("Sports", "sports"),
            Genre("Super Power", "super-power"),
            Genre("Supernatural", "supernatural"),
            Genre("Tragedy", "tragedy"),
            Genre("Vampire", "vampire"),
            Genre("Webtoons", "webtoons"),
            Genre("Yaoi", "yaoi"),
            Genre("Yuri", "yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

}
