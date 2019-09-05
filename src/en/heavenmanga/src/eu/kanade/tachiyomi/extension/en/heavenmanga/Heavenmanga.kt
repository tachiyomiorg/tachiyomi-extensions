package eu.kanade.tachiyomi.extension.en.heavenmanga

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


class Heavenmanga : ParsedHttpSource() {

    override val name = "Heaven Manga"

    override val baseUrl = "http://ww2.heavenmanga.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // selectors
    override fun popularMangaSelector() = "div.comics-grid div.entry"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun chapterListSelector() = "div.chapters-wrapper div.two-rows a"

    override fun popularMangaNextPageSelector() = "a.next[href^=http:]:has(i.fa-angle-right)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list/page-$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val item = element.select("h3 a")
        manga.url = item.attr("href")
        manga.title = item.text()
        manga.thumbnail_url = element.select("a.thumb > img").attr("src")
        return manga
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-update/page-$page", headers)
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/?s=$query"
        } else {
            var ret = String()
            var genre:String
            var name:String
            var authorFilter: String? = null
            filters.forEach { filter ->
                when (filter) {
                    is TextField -> {
                        if (filter.key == "author" && !filter.state.isEmpty()) {
                            authorFilter = filter.state
                            if (!authorFilter.isNullOrEmpty()) {
                                ret = "$baseUrl/author/$authorFilter/page-$page"
                            }
                        }

                    }
                    is GenreFilter -> {
                        if(filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            genre = if(name == "completed") "completed" else "genre/$name"
                            ret = "$baseUrl/$genre/page-$page"
                        }
                    }
                }
            }
            ret
        }

        return GET(url, headers)
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // details
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.comic-info")

        val manga = SManga.create()
        val author = infoElement.select("div.author").text()
        val genre = infoElement.select("div.genre").text()
        val status = infoElement.select("div.update > span:eq(1)").text()

        manga.title = infoElement.select("h1.name").text()
        manga.author = author.substring(author.indexOf(":") + 2)
        manga.status = parseStatus(status)
        manga.genre = genre.substring(genre.indexOf(":") + 2)
        manga.description = document.select("div.comic-description p").text()
        manga.thumbnail_url = infoElement.select("div.thumb img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapter
    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    // copied from en-holymanga extension
    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        val paginationSelector = latestUpdatesNextPageSelector()
        var document = response.asJsoup()
        var dateIndex = 0
        var continueParsing = true

        // Chapter list is paginated
        while (continueParsing) {
            // Chapter titles and urls
            document.select(chapterListSelector()).map{allChapters.add(chapterFromElement(it))}
            // Chapter dates
            document.select("div.chapter-date").forEach {
                if (it.hasText()) allChapters[dateIndex].date_upload = parseDate(it.text())
                dateIndex++
            }
            // Next page of chapters
            if (document.select(paginationSelector).isNotEmpty()) {
                document = client.newCall(GET(document.select(paginationSelector)
                    .attr("href"), headers)).execute().asJsoup()
            } else {
                continueParsing = false
            }
        }

        return allChapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(date).time
    }

    // pages
    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.chapter-content img").forEach {
            val url = it.attr("src")
            if (url != "") {
                pages.add(Page(pages.size, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // filters
    private class TextField(name: String, val key: String) : Filter.Text(name)

    override fun getFilterList() = FilterList(
        Filter.Header("Filters cannot be used while searching."),
        Filter.Separator(),
        Filter.Header("An invalid author will return mangas from all authors."),
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
