package eu.kanade.tachiyomi.extension.id.neumanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONArray
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
                .addQueryParameter("advpage", page.toString())
                .addQueryParameter("name_search_mode", "contain")
                .addQueryParameter("artist_search_mode", "contain")
                .addQueryParameter("author_search_mode", "contain")
                .addQueryParameter("year_search_mode", "on")
                .addQueryParameter("rating_search_mode", "is")
                .addQueryParameter("name_search_query", query)

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    val genreExclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        } else if (it.state == 2){
                            genreExclude.add(it.id)
                        }
                    }
                    url.addQueryParameter("genre1", JSONArray(genreInclude).toString())
                    url.addQueryParameter("genre2", JSONArray(genreExclude).toString())
                }
                is SelectField -> url.addQueryParameter(filter.key, filter.values[filter.state])
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
        manga.thumbnail_url = mangaInformationWrapper.select("img.imagemg").first().attr("src")
        manga.description = document.select(".summary").first().textNodes()[1].toString()
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
    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class SelectField(name: String, val key: String, values: Array<String>, state: Int = 0) : Filter.Select<String>(name, values, state)

    override fun getFilterList() = FilterList(
        SelectField("Sort", "sortby", arrayOf("rating", "name", "views", "latest")),
        TextField("Author", "author_search_query"),
        TextField("Artist", "artist_search_query"),
        TextField("Release Year", "year_value"),
        Status(),
        GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("Adventure", "Adventure"),
        Genre("Demons", "Demons"),
        Genre("fighting", "fighting"),
        Genre("Horor", "Horor"),
        Genre("legend", "legend"),
        Genre("Manhua", "Manhua"),
        Genre("Mecha", "Mecha"),
        Genre("Romance", "Romance"),
        Genre("neco", "neco"),
        Genre("Seinen", "Seinen"),
        Genre("Slice Of Life", "Slice Of Life"),
        Genre("Superhero", "Superhero"),
        Genre("Tragedy", "Tragedy"),
        Genre("Vampire", "Vampire"),
        Genre("Supernatural", "Supernatural"),
        Genre("Shoujo", "Shoujo"),
        Genre("Smut", "Smut"),
        Genre("School", "School"),
        Genre("Oneshot", "Oneshot"),
        Genre("Miatery", "Miatery"),
        Genre("Manhwa", "Manhwa"),
        Genre("live School", "live School"),
        Genre("Horror", "Horror"),
        Genre("Game", "Game"),
        Genre("Antihero", "Antihero"),
        Genre("Action", "Action"),
        Genre("Comedy", "Comedy"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Action. Adventure", "Action. Adventure"),
        Genre("Gender Bender", "Gender Bender"),
        Genre("Inaka", "Inaka"),
        Genre("Lolicon", "Lolicon"),
        Genre("Adult", "Adult"),
        Genre("Cooking", "Cooking"),
        Genre("Harem", "Harem"),
        Genre("Isekai", "Isekai"),
        Genre("Magic", "Magic"),
        Genre("Music", "Music"),
        Genre("Martial Arts", "Martial Arts"),
        Genre("Project", "Project"),
        Genre("sci fi", "sci fi"),
        Genre("Shounen", "Shounen"),
        Genre("Military", "Military"),
        Genre("Martial Art", "Martial Art"),
        Genre("Over Power", "Over Power"),
        Genre("School Life", "School Life"),
        Genre("Shoujo Ai", "Shoujo Ai"),
        Genre("sport", "sport"),
        Genre("Supranatural", "Supranatural"),
        Genre("Webtoon", "Webtoon"),
        Genre("Webtoons", "Webtoons"),
        Genre("Suspense", "Suspense"),
        Genre("Sports", "Sports"),
        Genre("Yuri", "Yuri"),
        Genre("Thriller", "Thriller"),
        Genre("Super Power", "Super Power"),
        Genre("ShounenS", "ShounenS"),
        Genre("Sci-fi", "Sci-fi"),
        Genre("Psychological", "Psychological"),
        Genre("Mystery", "Mystery"),
        Genre("Mature", "Mature"),
        Genre("Manga", "Manga"),
        Genre("Josei", "Josei"),
        Genre("Historical", "Historical"),
        Genre("Fantasy", "Fantasy"),
        Genre("Dachima", "Dachima"),
        Genre("Advanture", "Advanture"),
        Genre("Echi", "Echi"),
        Genre("4-Koma", "4-Koma")
    )

}