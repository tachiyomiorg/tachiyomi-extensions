package eu.kanade.tachiyomi.extension.id.neumanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class Neumanga : ParsedHttpSource() {

    override val id: Long = 2

    override val name = "Neumanga"

    override val baseUrl = "https://neumanga.tv"

    override val lang = "id"

    override val supportsLatest = true

    private val trustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }
    }

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }

    override val client = super.client.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()

    override fun popularMangaSelector() = "div#gov-result div.bolx"

    override fun latestUpdatesSelector() = "div#gov-result div.bolx"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/advanced_search?sortby=rating&advpage=$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/advanced_search?sortby=latest&advpage=$page", headers)
    }

    private fun mangaFromElement(query: String, element: Element): SManga {
        val manga = SManga.create()
        element.select(query).first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return mangaFromElement("h2 a", element)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return mangaFromElement("h2 a", element)
    }

    override fun popularMangaNextPageSelector() = "div#gov-result ul.pagination li.active + li a"

    override fun latestUpdatesNextPageSelector() = "div#gov-result ul.pagination li.active + li a"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/advanced_search")!!.newBuilder()
                .addQueryParameter("sortby", "name")
                .addQueryParameter("advpage", page.toString())
                .addQueryParameter("name_search_mode", "contain")
                .addQueryParameter("artist_search_mode", "contain")
                .addQueryParameter("author_search_mode", "contain")
                .addQueryParameter("year_search_mode", "on")
                .addQueryParameter("rating_search_mode", "is")
                .addQueryParameter("name_search_query", query)

        // TODO:
        // - create filter by genre
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                // is GenreList -> filter.state.forEach { genre -> url.addQueryParameter(genre.id, genre.state.toString()) }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div#gov-result div.bolx"

    override fun searchMangaFromElement(element: Element): SManga {
        return mangaFromElement("h2 a", element)
    }

    override fun searchMangaNextPageSelector() = "div#gov-result ul.pagination li.active + li a"

    override fun mangaDetailsParse(document: Document): SManga {
        val mangaInformationWrapper = document.select("#main .info").first()

        val manga = SManga.create()
        manga.author = mangaInformationWrapper.select("span a[href*=author_search_mode]").first().text()
        manga.artist = mangaInformationWrapper.select("span a[href*=artist_search_mode]").first().text()
        manga.genre = mangaInformationWrapper.select("a[href*=genre]").map { it.text() }.joinToString()
        manga.description = document.select(".summary").text()
        manga.thumbnail_url = mangaInformationWrapper.select("img.imagemg").first().attr("src")
        manga.status = parseStatus(mangaInformationWrapper.select("span a[href*=manga_status]").first().text())

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("ongoing") -> SManga.ONGOING
        status.contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapter .item:first-child .item-content a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href") + "/1")
        chapter.name = element.select("h3").text()
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".readnav select.page").first()?.getElementsByTag("option")?.forEach {
            pages.add(Page(pages.size, it.attr("value")))
        }
        pages.getOrNull(0)?.imageUrl = imageUrlParse(document)
        return pages
    }

    override fun imageUrlParse(document: Document) = document.select(".readarea img.imagechap").attr("src")

    private class Status : Filter.TriState("Completed")
    private class TextField(name: String, val key: String) : Filter.Text(name)

    private class Genre(name: String, val id: String = "genres[$name]") : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
            TextField("Author", "author_search_query"),
            TextField("Artist", "artist_search_query"),
            // GenreList(getGenreList()),
            Status()
    )

    private fun getGenreList() = listOf(
            Genre("Action"),
            Genre("Adventure"),
            Genre("Comedy"),
            Genre("Doujinshi"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Gender Bender"),
            Genre("Harem"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Josei"),
            Genre("Martial Arts"),
            Genre("Mature"),
            Genre("Mecha"),
            Genre("Mystery"),
            Genre("One Shot"),
            Genre("Psychological"),
            Genre("Romance"),
            Genre("School Life"),
            Genre("Sci-fi"),
            Genre("Seinen"),
            Genre("Shoujo"),
            Genre("Shoujo Ai"),
            Genre("Shounen"),
            Genre("Shounen Ai"),
            Genre("Slice of Life"),
            Genre("Sports"),
            Genre("Supernatural"),
            Genre("Tragedy"),
            Genre("Yaoi"),
            Genre("Yuri")
    )

}