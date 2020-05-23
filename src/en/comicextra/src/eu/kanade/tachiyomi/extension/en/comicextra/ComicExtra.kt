package eu.kanade.tachiyomi.extension.en.comicextra

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ComicExtra : ParsedHttpSource() {

    override val name = "ComicExtra"

    override val baseUrl = "https://www.comicextra.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val datePattern = Pattern.compile("(\\d+) days? ago")

    override fun popularMangaSelector() = "div.cartoon-box"

    override fun latestUpdatesSelector() = "div.hl-box"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/popular-comic", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic-updates", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/comic-search?key=$query", headers)
        } else {
            var url = baseUrl
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> url += "/${filter.toUriPart()}"
                }
            }
            GET(url + if (page > 1) "/$page" else "", headers)
        }
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.mb-right > h3 > a").attr("href"))
        title = element.select("div.mb-right > h3 > a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.hlb-t > a").attr("href"))
        title = element.select("div.hlb-t > a").text()
        thumbnail_url = fetchThumbnailURL(element.select("div.hlb-t > a").attr("href"))
    }

    private fun fetchThumbnailURL(url: String) = client.newCall(GET(url, headers)).execute().asJsoup().select("div.movie-l-img > img").attr("src")

    private fun fetchChaptersFromNav(url: String) = client.newCall(GET(url, headers)).execute().asJsoup().select(chapterListSelector())

    override fun popularMangaNextPageSelector() = "div.general-nav > a:contains(Next)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.movie-detail span.title-1").text()
            thumbnail_url = document.select("div.movie-l-img > img").attr("src")
            status = parseStatus(document.select("dt:contains(Status:) + dd").text())
            author = document.select("dt:contains(Author:) + dd").text()
            description = document.select("div#film-content").text()
            genre = document.select("dt.movie-dt:contains(Genres:) + dd a").joinToString { it.text() }
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {

        val document = response.asJsoup()
        val nav = document.getElementsByClass("general-nav").first()
        val chapters = ArrayList<SChapter>()

        document.select(chapterListSelector()).forEach {
            chapters.add(chapterFromElement(it))
        }

        if (nav == null) {
            return chapters
        }

        val links = nav.getElementsByTag("a")

        links.forEach {
            if (it.text() != "Next") {
                fetchChaptersFromNav(it.attr("href")).forEach { page ->
                    chapters.add(chapterFromElement(page))
                }
            }
        }

        return chapters
    }

    override fun chapterListSelector() = "table.table > tbody#list > tr:has(td)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlEl = element.select("td:nth-of-type(1) > a").first()
        val dateEl = element.select("td:nth-of-type(2)")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlEl.attr("href"))
        chapter.name = urlEl.text()
        chapter.date_upload = dateEl.text()?.let { dateParse(it) } ?: 0
        return chapter
    }

    private fun dateParse(dateAsString: String): Long {
        val date: Date
        date = try {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateAsString.replace(Regex("(st|nd|rd|th)"), ""))
        } catch (e: ParseException) {
            val m = datePattern.matcher(dateAsString)

            if (dateAsString != "Today" && m.matches()) {
                val amount = m.group(1).toInt()

                Calendar.getInstance().apply {
                    add(Calendar.DATE, -amount)
                }.time
            } else if (dateAsString == "Today") {
                Calendar.getInstance().time
            } else return 0
        }

        return date.time
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url + "/full", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img.chapter_img").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Unused method was called somehow!")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Note: can't combine search types"),
        Filter.Separator(),
        GenreFilter(getGenreList)
    )

    private class GenreFilter(genrePairs: Array<Pair<String, String>>) : UriPartFilter("Category", genrePairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private val getGenreList = arrayOf(
        Pair("Action", "action-comic"),
        Pair("Adventure", "adventure-comic"),
        Pair("Anthology", "anthology-comic"),
        Pair("Anthropomorphic", "anthropomorphic-comic"),
        Pair("Biography", "biography-comic"),
        Pair("Black Mask Studios", "black-mask-studios-comic"),
        Pair("Children", "children-comic"),
        Pair("Comedy", "comedy-comic"),
        Pair("Crime", "crime-comic"),
        Pair("DC Comics", "dc-comics-comic"),
        Pair("Dark Horse", "dark-horse-comic"),
        Pair("Drama", "drama-comic"),
        Pair("Family", "family-comic"),
        Pair("Fantasy", "fantasy-comic"),
        Pair("Fighting", "fighting-comic"),
        Pair("First Second Books", "first-second-books-comic"),
        Pair("Graphic Novels", "graphic-novels-comic"),
        Pair("Historical", "historical-comic"),
        Pair("Horror", "horror-comic"),
        Pair("LEOMACS", "a><span-class=-comic"),
        Pair("LGBTQ", "lgbtq-comic"),
        Pair("Leading Ladies", "leading-ladies-comic"),
        Pair("Literature", "literature-comic"),
        Pair("Manga", "manga-comic"),
        Pair("Martial Arts", "martial-arts-comic"),
        Pair("Marvel", "marvel-comic"),
        Pair("Mature", "mature-comic"),
        Pair("Military", "military-comic"),
        Pair("Movie Cinematic Link", "movie-cinematic-link-comic"),
        Pair("Movies & TV", "movies-&-tv-comic"),
        Pair("Music", "music-comic"),
        Pair("Mystery", "mystery-comic"),
        Pair("Mythology", "mythology-comic"),
        Pair("New", "new-comic"),
        Pair("Personal", "personal-comic"),
        Pair("Political", "political-comic"),
        Pair("Post-Apocalyptic", "post-apocalyptic-comic"),
        Pair("Psychological", "psychological-comic"),
        Pair("Pulp", "pulp-comic"),
        Pair("Religious", "religious-comic"),
        Pair("Robots", "robots-comic"),
        Pair("Romance", "romance-comic"),
        Pair("School Life", "school-life-comic"),
        Pair("Sci-Fi", "sci-fi-comic"),
        Pair("Slice of Life", "slice-of-life-comic"),
        Pair("Sport", "sport-comic"),
        Pair("Spy", "spy-comic"),
        Pair("Superhero", "superhero-comic"),
        Pair("Supernatural", "supernatural-comic"),
        Pair("Suspense", "suspense-comic"),
        Pair("Thriller", "thriller-comic"),
        Pair("Vampires", "vampires-comic"),
        Pair("Video Games", "video-games-comic"),
        Pair("War", "war-comic"),
        Pair("Western", "western-comic"),
        Pair("Zombies", "zombies-comic"),
        Pair("Zulema Scotto Lavina", "zulema-scotto-lavina-comic")
    )
}
