package eu.kanade.tachiyomi.extension.id.bacamanga

import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BacaManga : ParsedHttpSource() {

    override val name = "BacaManga"
    override val baseUrl = "https://bacamanga.cc"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/komik-populer/page/$page/", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
            val pattern = "\\s+".toRegex()
            val q = query.replace(pattern, "+")
            if (query.isNotEmpty()) {
                url.addQueryParameter("s", q)
            } else {
                url.addQueryParameter("s", "")
            }
            url.toString()
        } else {
            val url = HttpUrl.parse("$baseUrl/daftar-komik/page/$page")!!.newBuilder()
            var orderBy: String
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is Status -> url.addQueryParameter("status", arrayOf("", "ongoing", "completed")[filter.state])
                    is GenreList -> {
                        val genreInclude = mutableListOf<String>()
                        filter.state.forEach {
                            if (it.state == 1) {
                                genreInclude.add(it.id)
                            }
                        }
                        if (genreInclude.isNotEmpty()) {
                            genreInclude.forEach { genre ->
                                url.addQueryParameter("genre[]", genre)
                            }
                        }
                    }
                    is SortBy -> {
                        orderBy = filter.toUriPart()
                        url.addQueryParameter("order", orderBy)
                    }
                }
            }
            url.toString()
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "div.animepost"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("data-lazy-src")
        manga.setUrlWithoutDomain(element.select(".bigor > a").attr("href"))
        manga.title = element.select(".bigor .tt h2").text()
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".infox").first()
        val sepName = infoElement.select(".spe > span:nth-child(4)").last()
        val manga = SManga.create()
        manga.author = infoElement.select(".spe span:contains(Pengarang)").text().replace("Pengarang: ", "").trim()
        manga.artist = sepName.ownText()
        val genres = mutableListOf<String>()
        infoElement.select(".genre-info > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select(".spe span:contains(Status)").text())
        manga.description = document.select("div[^itemprop]").last().text()
        manga.thumbnail_url = document.select(".thumb noscript img").first().attr("src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {

        element.toLowerCase().contains("berjalan") -> SManga.ONGOING
        element.toLowerCase().contains("tamat") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#chapter_list ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".lchx a").first()
        val timeElement = element.select("span.rightoff").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = 0
        return chapter
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
        val scriptToParse = document.select("script[src*=cache]").first().attr("src")
        val slideaid = client.newCall(GET(scriptToParse, headers)).execute().body()!!.string()
        val imagesList = slideaid.substringAfter("var imgch").substringBefore(";").substringAfter("=").trim()
        val img_url = slideaid.substringAfter("#chimg").substringBefore("onError").substringAfter("src=\"").substringBefore("'").trim()
        val json = JsonParser().parse(imagesList).asJsonArray
        json.forEachIndexed { i, url ->
            val url_clean = url.toString().removeSurrounding("\"")
            // BASE URL HARD CODED
            pages.add(Page(i, "", "$img_url$url_clean"))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val headers = Headers.Builder()
        headers.apply {
            add("Referer", baseUrl)
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; LGMS323 Build/KOT49I.MS32310c) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.100 Mobile Safari/537.36")
        }

        if (page.imageUrl!!.contains("i0.wp.com")) {
            headers.apply {
                add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
            }
        }
        return GET(page.imageUrl!!, headers.build())
    }

    private class SortBy : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular")
        )
    )

    private class Status : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed")
        )
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortBy(),
        Filter.Separator(),
        Status(),
        Filter.Separator(),
        GenreList(getGenreList())
    )

    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Demon", "demon"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Genres: Action", "genres-action"),
        Genre("Gore", "gore"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horor", "horor"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Lolicon", "lolicon"),
        Genre("Magic", "magic"),
        Genre("Manhua", "manhua"),
        Genre("Martial Art", "martial-art"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Mistery", "mistery"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Project", "project"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Romance", "romance"),
        Genre("School", "school"),
        Genre("School Life", "school-life"),
        Genre("school of life", "school-of-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("sepernatural", "sepernatural"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoons", "webtoons"),
        Genre("Worn and Torn Newbie", "worn-and-torn-newbie"),
        Genre("Yuri", "yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
