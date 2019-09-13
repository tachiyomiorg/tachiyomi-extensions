package eu.kanade.tachiyomi.extension.id.maidmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.StringBuilder

class MaidManga : ParsedHttpSource() {

    override val name = "Maid - Manga"

    override val baseUrl = "https://www.maid.my.id"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesSelector() = "h2:contains(Update Chapter) + div.row div.col-12"

    override fun latestUpdatesRequest(page: Int):  Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl =  if(page == 1) "${baseUrl}" else "${baseUrl}/page/$page/"
        return GET(builtUrl)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("h3 a")
        val imgurl = element.select("div.limit img").attr("src").replace("?resize=100,140", "")
        manga.url = item.attr("href")
        manga.title = item.text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a:containsOwn(Berikutnya)"

    override fun popularMangaRequest(page: Int):  Request {
        val builtUrl =  if(page == 1) "${baseUrl}/advanced-search/?order=popular" else "${baseUrl}/advanced-search/page/$page/?order=popular"
        return GET(builtUrl)
    }

    override fun popularMangaSelector() = "div.row div.col-6"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val imgurl = element.select("div.card img").attr("src").replace("?resize=165,225", "")
        manga.url = element.select("div.card a").attr("href")
        manga.title = element.select("div.card img").attr("title")
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl =  if(page == 1) "${baseUrl}/advanced-search/" else "${baseUrl}/advanced-search/page/$page/"
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                            .filter { it.state != Filter.TriState.STATE_IGNORE }
                            .forEach { url.addQueryParameter("genre[]", it.id) }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val stringBuilder = StringBuilder()
        val infoElement = document.select("div.infox")
        val author = document.select("span:contains(author)").text().substringAfter("Author: ").substringBefore(" (")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = document.select("span:contains(Status)").text()
        val desc = document.select("div.sinopsis p")
        infoElement.select("div.gnr a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        if (desc.size > 0) {
            desc.forEach {
                stringBuilder.append(cleanDesc(it.text()))
                if (it != desc.last())
                    stringBuilder.append("\n\n")
            }
        }
        manga.title = infoElement.select("h1").text()
        manga.author = author
        manga.artist = author
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.description = stringBuilder.toString()
        manga.thumbnail_url = document.select("div.bigcontent img").attr("src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Status: Ongoing") -> SManga.ONGOING
        status.contains("Status: Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "ul#chapter_list li a:contains(chapter)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a:contains(chapter)")
        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div#readerarea img").forEach {
            val url = it.attr("src")
            if (url != "") {
                pages.add(Page(pages.size, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
            Filter.Header("You can combine filter."),
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreList(getGenreList())
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter("Type", arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("One-Shot", "One-Shot"),
            Pair("Doujin", "Doujin")
    ))
    private class OrderByFilter : UriPartFilter("Order By", arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating")
    ))

    private fun getGenreList() = listOf(
            Tag("4-koma", "4-Koma"),
            Tag("4-koma-comedy", "4-Koma Comedy"),
            Tag("action", "Action"),
            Tag("adult" ,"Adult"),
            Tag("adventure", "Adventure"),
            Tag("comedy", "Comedy"),
            Tag("demons", "Demons"),
            Tag("drama", "Drama"),
            Tag("ecchi", "Ecchi"),
            Tag("fantasy", "Fantasy"),
            Tag("game", "Game"),
            Tag("gender-bender", "Gender bender"),
            Tag("gore", "Gore"),
            Tag("harem", "Harem"),
            Tag("historical", "Historical"),
            Tag("horror", "Horror"),
            Tag("isekai", "Isekai"),
            Tag("josei", "Josei"),
            Tag("loli", "Loli"),
            Tag("magic", "Magic"),
            Tag("manga", "Manga"),
            Tag("manhua", "Manhua"),
            Tag("manhwa", "Manhwa"),
            Tag("martial-arts", "Martial Arts"),
            Tag("mature", "Mature"),
            Tag("mecha", "Mecha"),
            Tag("military", "Military"),
            Tag("monster-girls", "Monster Girls"),
            Tag("music", "Music"),
            Tag("mystery", "Mystery"),
            Tag("one-shot", "One Shot"),
            Tag("parody", "Parody"),
            Tag("police", "Police"),
            Tag("psychological", "Psychological"),
            Tag("romance", "Romance"),
            Tag("school", "School"),
            Tag("school-life", "School Life"),
            Tag("sci-fi", "Sci-Fi"),
            Tag("socks", "Socks"),
            Tag("seinen", "Seinen"),
            Tag("shoujo", "Shoujo"),
            Tag("shoujo-ai", "Shoujo Ai"),
            Tag("shounen", "Shounen"),
            Tag("shounen-ai", "Shounen Ai"),
            Tag("slice-of-life", "Slice of Life"),
            Tag("smut", "Smut"),
            Tag("sports", "Sports"),
            Tag("super-power", "Super Power"),
            Tag("supernatural", "Supernatural"),
            Tag("survival", "Survival"),
            Tag("thriller", "Thriller"),
            Tag("tragedy", "Tragedy"),
            Tag("vampire", "Vampire"),
            Tag("webtoons", "Webtoons"),
            Tag("yuri", "Yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.TriState(name)

    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)

    private fun cleanDesc(desc: String): String = desc.replace(Regex("\\(.*\\)"), "").trim()
}