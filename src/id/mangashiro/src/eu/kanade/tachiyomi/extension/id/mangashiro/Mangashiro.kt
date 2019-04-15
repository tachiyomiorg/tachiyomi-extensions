package eu.kanade.tachiyomi.extension.id.mangashiro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.source.model.*
//import java.text.SimpleDateFormat
//import java.util.*
//import java.text.ParseException

class Mangashiro: ParsedHttpSource() {

    override val name = "Mangashiro"
    override val baseUrl = "https://mangashiro.net"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-manga/page/$page?order=popular", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/daftar-manga/page/$page?order=update", headers)
    }
    //    LIST SELECTOR
    override fun popularMangaSelector() = "div.utao"
    override fun latestUpdatesSelector() =  popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    //    ELEMENT
    override fun popularMangaFromElement(element: Element): SManga =  searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga =  searchMangaFromElement(element)

    //    NEXT SELECTOR
    override fun popularMangaNextPageSelector() = "div.pagination > a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element):SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.imgu > a.series > img").attr("src")
        element.select("div .imgu > a.series").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
        val pattern = "\\s+".toRegex()
        val q = query.replace(pattern, "+")
        if(query.length > 0){
            url.addQueryParameter("s", q)
        }else{
            url.addQueryParameter("s", "")
        }
        return GET(url.toString(), headers)
    }

    // max 200 results

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#content").first()
		val sepName = infoElement.select("div.listinfo > ul > li:nth-child(2)").last()

        val manga = SManga.create()
		manga.author = sepName.ownText().trim()
        manga.artist = sepName.ownText().trim()

        val genres = mutableListOf<String>()
        infoElement.select("div.gnr a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }

        manga.genre =genres.joinToString(", ")

        manga.status = parseStatus(infoElement.select("div.listinfo > ul > li:nth-child(4)").text())

        manga.description = document.select("span.desc > p")?.text()
        manga.thumbnail_url = document.select("div.side.infomanga > div > img").attr("src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {

        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li > div.rg > div.lch"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
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
        var i = 0
        document.select("div#readerarea p a img").forEach { element ->
            val url = element.attr("src")
            i++
            if(url.length != 0){
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }
    //    private class Status : Filter.TriState("Completed")
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class SortBy : UriPartFilter("Sort by", arrayOf(
            Pair("Relevance", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Rating", "rating"),
            Pair("Trending", "trending"),
            Pair("Most View", "views"),
            Pair("New", "new-manga")
    ))
    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    override fun getFilterList() = FilterList(
//            TextField("Judul", "title"),
            TextField("Author", "author"),
            TextField("Year", "release"),
            SortBy(),
            StatusList(getStatusList()),
            GenreList(getGenreList())
    )
    private fun getStatusList() = listOf(
            Status("Completed","end"),
            Status("Ongoing","on-going"),
            Status("Canceled","canceled"),
            Status("Onhold","on-hold")
    )
    private fun getGenreList() = listOf(
            Genre("Action","action"),
            Genre("Adventure","adventure"),
            Genre("Comedy","comedy"),
            Genre("Drama","drama"),
            Genre("Fantasy","fantasy"),
            Genre("Gender bender","gender-bender"),
            Genre("Gossip","gossip"),
            Genre("Harem","harem"),
            Genre("Historical","historical"),
            Genre("Horror","horror"),
            Genre("Incest","incest"),
            Genre("Martial arts","martial-arts"),
            Genre("Mecha","mecha"),
            Genre("Medical","medical"),
            Genre("Mystery","mystery"),
            Genre("One shot","one-shot"),
            Genre("Psychological","psychological"),
            Genre("Romance","romance"),
            Genre("School LIfe","school-life"),
            Genre("Sci Fi","sci-fi"),
            Genre("Slice of Life","slice-of-life"),
            Genre("Smut","smut"),
            Genre("Sports","sports"),
            Genre("Supernatural","supernatural"),
            Genre("Tragedy","tragedy"),
            Genre("Yaoi","yaoi"),
            Genre("Yuri","yuri")
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }


}
