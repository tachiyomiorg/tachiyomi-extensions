package eu.kanade.tachiyomi.extension.en.readcomiconline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.regex.Pattern

class Readcomiconline : ParsedHttpSource() {

    override val name = "ReadComicOnline"

    override val baseUrl = "https://readcomiconline.to"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "table.listing tr:gt(1)"

    override fun latestUpdatesSelector() = "table.listing tr:gt(1)"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ComicList/MostPopular?page=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("https://readcomiconline.to/ComicList/LatestUpdate?page=$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("td a:eq(0)").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "li > a:contains(Next)"

    override fun latestUpdatesNextPageSelector(): String = "ul.pager > li > a:contains(Next)"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("comicName", query)

            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is Author -> add("authorArtist", filter.state)
                    is Status -> add("status", arrayOf("", "Completed", "Ongoing")[filter.state])
                    is GenreList -> filter.state.forEach { genre -> add("genres", genre.state.toString()) }
                }
            }
        }
        return POST("$baseUrl/AdvanceSearch", headers, form.build())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.barContent").first()

        val manga = SManga.create()
        manga.artist = infoElement.select("p:has(span:contains(Artist:)) > a").first()?.text()
        manga.author = infoElement.select("p:has(span:contains(Writer:)) > a").first()?.text()
        manga.genre = infoElement.select("p:has(span:contains(Genres:)) > *:gt(0)").text()
        manga.description = infoElement.select("p:has(span:contains(Summary:)) ~ p").text()
        manga.status = infoElement.select("p:has(span:contains(Status:))").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select(".rightBox:eq(0) img").first()?.attr("src")
        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.listing tr:gt(1)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("td:eq(1)").first()?.text()?.let {
            SimpleDateFormat("MM/dd/yyyy").parse(it).time
        } ?: 0
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url+"&quality=hq", headers)

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        //language=RegExp
        val p = Pattern.compile("""lstImages.push\("(.+?)"""")
        val m = p.matcher(response.body().string())

        var i = 0
        while (m.find()) {
            pages.add(Page(i++, "", m.group(1)))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    private class Status : Filter.TriState("Completed")
    private class Author : Filter.Text("Author")
    private class Genre(name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            Author(),
            Status(),
            GenreList(getGenreList())
    )

    // $("select[name=\"genres\"]").map((i,el) => `Genre("${$(el).next().text().trim()}", ${i})`).get().join(',\n')
    // on https://readcomiconline.to/AdvanceSearch
    private fun getGenreList() = listOf(
            Genre("Action"),
            Genre("Adventure"),
            Genre("Anthology"),
            Genre("Anthropomorphic"),
            Genre("Biography"),
            Genre("Children"),
            Genre("Comedy"),
            Genre("Crime"),
            Genre("Drama"),
            Genre("Family"),
            Genre("Fantasy"),
            Genre("Fighting"),
            Genre("Graphic Novels"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Leading Ladies"),
            Genre("LGBTQ"),
            Genre("Literature"),
            Genre("Manga"),
            Genre("Martial Arts"),
            Genre("Mature"),
            Genre("Military"),
            Genre("Movies & TV"),
            Genre("Mystery"),
            Genre("Mythology"),
            Genre("Personal"),
            Genre("Political"),
            Genre("Post-Apocalyptic"),
            Genre("Psychological"),
            Genre("Pulp"),
            Genre("Religious"),
            Genre("Robots"),
            Genre("Romance"),
            Genre("School Life"),
            Genre("Sci-Fi"),
            Genre("Slice of Life"),
            Genre("Spy"),
            Genre("Superhero"),
            Genre("Supernatural"),
            Genre("Suspense"),
            Genre("Thriller"),
            Genre("Vampires"),
            Genre("Video Games"),
            Genre("War"),
            Genre("Western"),
            Genre("Zombies")
    )
}