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
            val url = HttpUrl.parse("$baseUrl/manga")!!
            // todo
            var status = ""
            var rank = ""
            var orderBy = ""
            var genres: Int
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        status = when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "completed"
                            Filter.TriState.STATE_EXCLUDE -> "ongoing"
                            else -> "all"
                        }
                    }
                    is RankFilter -> {
                        rank = filter.toUriPart()
                    }
                    is SortBy -> {
                        orderBy = filter.toUriPart()
                    }
                    is GenreList -> {
                        filter.state
                            .forEach { genres = it.state }
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

    private class StatusFilter : Filter.TriState("Completed")

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

    private class Genre(name: String, val id: String) : Filter.TriState(name)
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
            Genre("4-koma", "mrs-genre-100117675"),
            Genre("Action", "mrs-genre-304068"),
            Genre("Adult", "mrs-genre-358370"),
            Genre("Adventure", "mrs-genre-304087"),
            Genre("Comedy", "mrs-genre-304069"),
            Genre("Demons", "mrs-genre-304088"),
            Genre("Doujinshi", "mrs-genre-304197"),
            Genre("Drama", "mrs-genre-304177"),
            Genre("Ecchi", "mrs-genre-304074"),
            Genre("Fantasy", "mrs-genre-304089"),
            Genre("Gender Bender", "mrs-genre-304358"),
            Genre("Harem", "mrs-genre-304075"),
            Genre("Historical", "mrs-genre-304306"),
            Genre("Horror", "mrs-genre-304259"),
            Genre("Isekai", "mrs-genre-100291868"),
            Genre("Josei", "mrs-genre-304070"),
            Genre("Kids", "mrs-genre-304846"),
            Genre("Magic", "mrs-genre-304090"),
            Genre("Martial Arts", "mrs-genre-304072"),
            Genre("Mature", "mrs-genre-358371"),
            Genre("Mecha", "mrs-genre-304245"),
            Genre("Military", "mrs-genre-304091"),
            Genre("Music", "mrs-genre-304589"),
            Genre("Mystery", "mrs-genre-304178"),
            Genre("One Shot", "mrs-genre-100018505"),
            Genre("Parody", "mrs-genre-304786"),
            Genre("Police", "mrs-genre-304236"),
            Genre("Psychological", "mrs-genre-304176"),
            Genre("Romance", "mrs-genre-304073"),
            Genre("School Life", "mrs-genre-304076"),
            Genre("Sci-Fi", "mrs-genre-304180"),
            Genre("Seinen", "mrs-genre-304077"),
            Genre("Shoujo Ai", "mrs-genre-304695"),
            Genre("Shoujo", "mrs-genre-304175"),
            Genre("Shounen Ai", "mrs-genre-304307"),
            Genre("Shounen", "mrs-genre-304164"),
            Genre("Slice of Life", "mrs-genre-304195"),
            Genre("Smut", "mrs-genre-358372"),
            Genre("Space", "mrs-genre-305814"),
            Genre("Sports", "mrs-genre-304367"),
            Genre("Super Power", "mrs-genre-305270"),
            Genre("Supernatural", "mrs-genre-304067"),
            Genre("Tragedy", "mrs-genre-358379"),
            Genre("Vampire", "mrs-genre-304765"),
            Genre("Webtoons", "mrs-genre-358150"),
            Genre("Yaoi", "mrs-genre-304202"),
            Genre("Yuri", "mrs-genre-304690")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

}
