package eu.kanade.tachiyomi.extension.it.mangaworld

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Mangaworld : ParsedHttpSource() {

    override val name = "Mangaworld"
    override val baseUrl = "https://www.mangaworld.cc"
    override val lang = "it"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/archive?sort=most_read&page=$page", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/archive?sort=newest&page=$page", headers)
    }
    //    LIST SELECTOR
    override fun popularMangaSelector() = "div.comics-grid .entry"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    //    ELEMENT
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    //    NEXT SELECTOR
    //  Not needed
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    // ////////////////

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        // nextPage is not possible because pagination is loaded after via Javascript
        // 16 is the default manga-per-page. If it is less than 16 then there's no next page
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return latestUpdatesParse(response)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("a.thumb img").attr("src")
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return latestUpdatesParse(response)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/archive?page=$page")!!.newBuilder()
        val pattern = "\\s+".toRegex()
        val q = query
        if (query.length > 0) {
            url.addQueryParameter("keyword", q)
        } else {
            url.addQueryParameter("keyword", "")
        }

        var orderBy = ""

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre", genre)
                        }
                    }
                }
                is StatusList -> {
                    val statuses = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            statuses.add(it.id)
                        }
                    }
                    if (statuses.isNotEmpty()) {
                        statuses.forEach { status ->
                            url.addQueryParameter("status", status)
                        }
                    }
                }

                is MTypeList -> {
                    val typeslist = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            typeslist.add(it.id)
                        }
                    }
                    if (typeslist.isNotEmpty()) {
                        typeslist.forEach { mtype ->
                            url.addQueryParameter("type", mtype)
                        }
                    }
                }

                is SortBy -> {
                    orderBy = filter.toUriPart()
                    url.addQueryParameter("sort", orderBy)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }
        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.comic-info")
        val metaData = document.select("div.comic-info").first()

        val manga = SManga.create()
        manga.author = infoElement.select("a[href^=https://www.mangaworld.cc/archive?author=]").first()?.text()
        manga.artist = infoElement.select("a[href^=https://www.mangaworld.cc/archive?artist=]")?.text()

        val genres = mutableListOf<String>()
        metaData.select("div.meta-data a.badge").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("a[href^=https://www.mangaworld.cc/archive?status=]").first().attr("href"))

        manga.description = document.select("div#noidungm")?.text()
        manga.thumbnail_url = document.select(".comic-info .thumb > img").attr("src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {

        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapters-wrapper .chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a.chap").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(getUrl(urlElement))
        chapter.name = urlElement.select("span.d-inline-block").first().text()
        chapter.date_upload = element.select(".chap-date").last()?.text()?.let {
            try {
                SimpleDateFormat("dd MMMM yyyy", Locale.ITALY).parse(it).time
            } catch (e: ParseException) {
                SimpleDateFormat("H", Locale.ITALY).parse(it).time
            }
        } ?: 0
        return chapter
    }

    private fun getUrl(urlElement: Element): String {
        var url = urlElement.attr("href")
        return when {
            url.endsWith("?style=list") -> url
            else -> "$url?style=list"
        }
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Capitolo\s([0-9]+)""")
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
        document.select("div#page img.page-image").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.length != 0) {
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
    private class SortBy : UriPartFilter(
        "Ordina per",
        arrayOf(
            Pair("Rilevanza", ""),
            Pair("Più recenti", "newest"),
            Pair("Meno recenti", "oldest"),
            Pair("A-Z", "a-z"),
            Pair("Z-A", "z-a"),
            Pair("Più letti", "most_read"),
            Pair("Meno letti", "less_read")
        )
    )
    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Generi", genres)
    private class MType(name: String, val id: String = name) : Filter.TriState(name)
    private class MTypeList(types: List<MType>) : Filter.Group<MType>("Tipologia", types)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

    override fun getFilterList() = FilterList(
        TextField("Anno di rilascio", "year"),
        SortBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
        MTypeList(getTypesList())
    )
    private fun getStatusList() = listOf(
        Status("In corso", "ongoing"),
        Status("Finito", "completed"),
        Status("Droppato", "dropped"),
        Status("In pausa", "paused"),
        Status("Cancellato", "canceled")
    )

    private fun getTypesList() = listOf(
        MType("Manga", "manga"),
        MType("Manhua", "manhua"),
        MType("Manhwa", "manhwa"),
        MType("Oneshot", "oneshot"),
        MType("Thai", "thai"),
        MType("Vietnamita", "vietnamese")
    )

    private fun getGenreList() = listOf(
        Genre("Adulti", "adulti"),
        Genre("Arti Marziali", "arti-marziali"),
        Genre("Avventura", "avventura"),
        Genre("Azione", "azione"),
        Genre("Commedia", "commedia"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drammatico", "drammatico"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Lolicon", "lolicon"),
        Genre("Maturo", "maturo"),
        Genre("Mecha", "mecha"),
        Genre("Mistero", "mistero"),
        Genre("Psicologico", "psicologico"),
        Genre("Romantico", "romantico"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Scolastico", "scolastico"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Soprannaturale", "soprannaturale"),
        Genre("Sport", "sport"),
        Genre("Storico", "storico"),
        Genre("Tragico", "tragico"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
