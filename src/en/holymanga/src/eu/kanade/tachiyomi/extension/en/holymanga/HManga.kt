package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

abstract class HManga (
    override val name: String,
    override val baseUrl: String
) : ParsedHttpSource() {

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    // This returns 12 manga or so, main browsing for this source should be through latest
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector() = "section#popular div.entry.vertical"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h2 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-update/page-$page/", headers)
    }

    override fun latestUpdatesSelector() = "div.comics-grid > div.entry"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").first().attr("src")
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a.next:has(i.fa-angle-right)"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/?s=$query"
        } else {
            lateinit var ret: String
            lateinit var genre: String
            filters.forEach { filter ->
                when (filter) {
                    is TextField -> {
                            if (filter.state.isNotBlank()) {
                                ret = "$baseUrl/author/${filter.state.replace(" ", "-")}/page-$page"
                            }
                    }
                    is GenreFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            filter.toUriPart().let { genre = if(it == "completed") "completed" else "genre/$it" }
                            ret = "$baseUrl/$genre/page-$page"
                        }
                    }
                }
            }
            ret
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.single-comic").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h1").first().text()
        manga.author = infoElement.select("div.author a").text()
        val status = infoElement.select("div.update span[style]").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("div.genre").text().substringAfter("Genre(s): ")
        manga.description = infoElement.select("div.comic-description p").text()
        manga.thumbnail_url = infoElement.select("img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "div.go-border"

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        var continueParsing = true

        // Chapter list is paginated
        while (continueParsing) {
            document.select(chapterListSelector()).map{ chapters.add(chapterFromElement(it)) }
            // Next page of chapters
            document.select("${latestUpdatesNextPageSelector()}:not([id])").let{
                if (it.isNotEmpty()) {
                    document = client.newCall(GET(it.attr("abs:href"), headers)).execute().asJsoup()
                } else {
                    continueParsing = false
                }
            }
        }
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select("a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text()
        }
        chapter.date_upload = parseChapterDate(element.select("div.chapter-date").text())

        return chapter
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("MM/dd/yyyy", Locale.US)
        }
    }

    private fun parseChapterDate(string: String): Long {
            return dateFormat.parse(string).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.chapter-content img").forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    private class TextField(name: String, val key: String) : Filter.Text(name)

    override fun getFilterList() = FilterList(
        Filter.Header("Cannot combine search types!"),
        Filter.Header("Author name must be exact."),
        Filter.Separator("-----------------"),
        TextField("Author", "author"),
        GenreFilter ()
    )

    // [...document.querySelectorAll('.sub-menu li a')].map(a => `Pair("${a.textContent}", "${a.getAttribute('href')}")`).join(',\n')
    // from $baseUrl
    private class GenreFilter: UriPartFilter("Genres",
        arrayOf(
            Pair("Choose a genre", ""),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Anime", "anime"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Comic", "comic"),
            Pair("Completed", "completed"),
            Pair("Cooking", "cooking"),
            Pair("Doraemon", "doraemon"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Full Color", "full-color"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Josei", "josei"),
            Pair("Live action", "live-action"),
            Pair("Magic", "magic"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Mystery", "mystery"),
            Pair("One shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Trap", "trap"),
            Pair("Webtoons", "webtoons")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
