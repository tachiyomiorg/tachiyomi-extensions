package eu.kanade.tachiyomi.extension.id.komikindo

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class KomikIndo : ParsedHttpSource() {

    override val name = "Komik Indo"
    override val baseUrl = "https://www.komikindo.web.id"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl" else "$baseUrl/page/$page"
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page"
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var builtUrl = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        if (!query.equals("")) {
            builtUrl = if (page == 1) "$baseUrl/search/$query/" else "$baseUrl/search/$query/page/$page/"
        } else if (filters.size > 0) {
            filters.forEach { filter ->
                when (filter) {
                    is GenreListFilter -> {
                        builtUrl = if (page == 1) "$baseUrl/genres/" + filter.toUriPart() else "$baseUrl/genres/" + filter.toUriPart() + "/page/$page/"
                    }
                }
            }
        }
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "div.lchap > .lch > .ch"
    override fun latestUpdatesSelector() = "div.ctf > div.lsmin > div.chl"
    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.thumbnail img").first().attr("src")
        element.select("div.l > h3 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.thumbnail img").first().attr("src")
        element.select("div.chlf > h2 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElm = document.select(".listinfo > ul > li")
        val manga = SManga.create()
        infoElm.forEachIndexed { index, element ->
            val infoTitle = element.select("b").text()
            var infoContent = element.text()
            when {
                infoTitle.toLowerCase().contains("type") -> println("position is 1")
                infoTitle.toLowerCase().contains("status") -> manga.status = parseStatus(infoContent)
                infoTitle.toLowerCase().contains("chapter") -> println("position is 1")
                infoTitle.toLowerCase().contains("author") -> manga.author = infoContent
                infoTitle.toLowerCase().contains("artist") -> manga.artist = infoContent
                infoTitle.toLowerCase().contains("genres") -> {
                    val genres = mutableListOf<String>()
                    element.select("a").forEach { element ->
                        val genre = element.text()
                        genres.add(genre)
                    }
                    manga.genre = genres.joinToString(", ")
                }
            }
        }
        manga.description = document.select("div.rm > span > p:first-child").text()
        manga.thumbnail_url = document.select("div.animeinfo .lm .imgdesc img:first-child").attr("src")
        return manga
    }

    private fun parseInfo(element: String): Int = when {
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.cl ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".leftoff > a").first()
        val chapter = SChapter.create()
        val timeElement = element.select("span.rightoff").first()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        val sdf = SimpleDateFormat("MMM dd, yyyy")
        val parse = sdf.parse(date)
        val cal = Calendar.getInstance()
        cal.time = parse
        return cal.timeInMillis
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div#readerarea img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.length != 0) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.100 Safari/537.36")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    private class GenreListFilter : UriPartFilter("Genre", arrayOf(
        Pair("Default", ""),
        Pair("4-Koma", "4-koma"),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Crime", "crime"),
        Pair("Dark Fantasy", "dark-fantasy"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horor", "horor"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Komik Tamat", "komik-tamat"),
        Pair("Life", "life"),
        Pair("Magic", "magic"),
        Pair("Manhua", "manhua"),
        Pair("Martial Art", "martial-art"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Post-Apocalyptic", "post-apocalyptic"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School", "school"),
        Pair("School Life", "school-life"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shonen", "shonen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Super Power", "super-power"),
        Pair("Superheroes", "superheroes"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Zombies", "zombies")
    ))

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: filter will be ignored when using text search!"),
        GenreListFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
