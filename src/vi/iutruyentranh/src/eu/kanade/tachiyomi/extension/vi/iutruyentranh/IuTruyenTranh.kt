package eu.kanade.tachiyomi.extension.vi.iutruyentranh

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
import java.text.SimpleDateFormat

class IuTruyenTranh : ParsedHttpSource() {

    override val name = "IuTruyenTranh"

    override val baseUrl = "http://iutruyentranh.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.bbottom h4.media-heading"

    override fun latestUpdatesSelector() = "h4.media-heading"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/genre/$page?popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:contains(...»)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search/$page?")!!.newBuilder().addQueryParameter("name", query)
        val genres = mutableListOf<String>()
        val genresEx = mutableListOf<String>()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Author -> url.addQueryParameter("autart", filter.state)
                is GenreList -> filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> genres.add(genre.name.toLowerCase())
                        Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.name.toLowerCase())
                    }
                }
            }
        }
        if (genres.isNotEmpty()) url.addQueryParameter("genres", genres.joinToString(","))
        if (genresEx.isNotEmpty()) url.addQueryParameter("genres-exclude", genresEx.joinToString(","))

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("section.manga article").first()

        val manga = SManga.create()
        manga.author = infoElement.select("span[itemprop=author]").first()?.text()
        manga.genre = infoElement.select("a[itemprop=genre]").text()
        manga.description = infoElement.select("p.box.box-danger").text()
        manga.status = infoElement.select("a[rel=nofollow]").last()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = infoElement.select("img[class^=thumbnail]").first()?.attr("src")
        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.list-unstyled > table > tbody > tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href") + "&load=all")
        chapter.name = urlElement.select("b").text()
        chapter.date_upload = element.select("td:eq(1)").first()?.text()?.let {
            SimpleDateFormat("dd/MM/yyyy").parse(it).time
        } ?: 0
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("img.img").forEach {
            pages.add(Page(i++, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    private class Author : Filter.Text("Tác giả")
    private class Genre(name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    override fun getFilterList() = FilterList(
            Author(),
            GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
            Genre("Action"),
            Genre("Adult"),
            Genre("Adventure"),
            Genre("Anime"),
            Genre("Bishounen"),
            Genre("Comedy"),
            Genre("Cookin"),
            Genre("Demons"),
            Genre("Doujinshi"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Gender Bender"),
            Genre("Harem"),
            Genre("Hentai"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Josei"),
            Genre("Live action"),
            Genre("Magic"),
            Genre("Manhua"),
            Genre("Manhwa"),
            Genre("Martial Arts"),
            Genre("Mature"),
            Genre("Mecha"),
            Genre("Medical"),
            Genre("Military"),
            Genre("Mystery"),
            Genre("One shot"),
            Genre("Oneshot"),
            Genre("Other"),
            Genre("Psychological"),
            Genre("Romance"),
            Genre("School Life"),
            Genre("Sci fi"),
            Genre("Seinen"),
            Genre("Shotacon"),
            Genre("Shoujo"),
            Genre("Shoujo Ai"),
            Genre("Shoujoai"),
            Genre("Shounen"),
            Genre("Shounen Ai"),
            Genre("Shounenai"),
            Genre("Slice of Life"),
            Genre("Smut"),
            Genre("Sports"),
            Genre("Super power"),
            Genre("Superma"),
            Genre("Supernatural"),
            Genre("Tragedy"),
            Genre("Vampire"),
            Genre("Webtoon"),
            Genre("Yaoi"),
            Genre("Yuri")
    )
}
