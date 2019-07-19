package eu.kanade.tachiyomi.extension.it.mangaworld

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import eu.kanade.tachiyomi.source.model.*
import java.text.ParseException

class Mangaworld: ParsedHttpSource() {

    override val name = "Mangaworld"
    override val baseUrl = "https://mangaworld.biz"
    override val lang = "it"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    }
    //    LIST SELECTOR
    override fun popularMangaSelector() = "div.c-tabs-item__content"
    override fun latestUpdatesSelector() =  popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    //    ELEMENT
    override fun popularMangaFromElement(element: Element): SManga =  searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga =  searchMangaFromElement(element)

    //    NEXT SELECTOR
    override fun popularMangaNextPageSelector() = "div.nav-previous.float-left > a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element):SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.tab-thumb > a > img").attr("src")
        element.select("div.tab-thumb > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
        url.addQueryParameter("post_type","wp-manga")
        val pattern = "\\s+".toRegex()
        val q = query.replace(pattern, "+")
        if(query.length > 0){
            url.addQueryParameter("s", q)
        }else{
            url.addQueryParameter("s", "")
        }

        var orderBy = ""

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
//                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                is GenreList -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if(genreInclude.isNotEmpty()){
                        genreInclude.forEach{ genre ->
                            url.addQueryParameter("genre[]", genre)
                        }
                    }
                }
                is StatusList ->{
                    val statuses = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            statuses.add(it.id)
                        }
                    }
                    if(statuses.isNotEmpty()){
                        statuses.forEach{ status ->
                            url.addQueryParameter("status[]", status)
                        }
                    }
                }

                is SortBy -> {
                    orderBy = filter.toUriPart();
                    url.addQueryParameter("m_orderby",orderBy)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }

        return GET(url.toString(), headers)
    }



    // max 200 results

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.site-content").first()

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content")?.text()
        manga.artist = infoElement.select("div.artist-content")?.text()

        val genres = mutableListOf<String>()
        infoElement.select("div.genres-content a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre =genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("div.post-status > div:nth-child(2)  div").text())

        manga.description = document.select("div.summary__content > p")?.text()
        manga.thumbnail_url = document.select("div.summary_image > a > img").attr("src")

        return manga
    }

    private fun parseStatus(element: String): Int = when {

        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(getUrl(urlElement))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("span.chapter-release-date i").last()?.text()?.let {
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
        document.select("div.reading-content * img").forEach { element ->
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
    private class SortBy : UriPartFilter("Ordina per", arrayOf(
            Pair("Rilevanza", ""),
            Pair("Ultime Aggiunte", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Voto", "rating"),
            Pair("Tendenza", "trending"),
            Pair("Più Visualizzati", "views"),
            Pair("Nuove Aggiunte", "new-manga")
    ))
    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Generi", genres)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

    override fun getFilterList() = FilterList(
//            TextField("Judul", "title"),
            TextField("Autore", "author"),
            TextField("Anno di rilascio", "release"),
            SortBy(),
            StatusList(getStatusList()),
            GenreList(getGenreList())
    )
    private fun getStatusList() = listOf(
            Status("Completato","end"),
            Status("In Corso","on-going"),
            Status("Droppato","canceled"),
            Status("In Pausa","on-hold")
    )
    private fun getGenreList() = listOf(
            Genre("Adulti","adult"),
            Genre("Anime","anime"),
            Genre("Arti Marziali","martial-arts"),
            Genre("Avventura","adventure"),
            Genre("Azione","action"),
            Genre("Cartoon","cartoon"),
            Genre("Comic","comic"),
            Genre("Commedia","comedy"),
            Genre("Cucina","cooking"),
            Genre("Demoni","demoni"),
            Genre("Detective","detective"),
            Genre("Doujinshi","doujinshi"),
            Genre("Drama","drama-"),
            Genre("Drammatico","drama"),
            Genre("Ecchi","ecchi"),
            Genre("Fantasy","fantasy"),
            Genre("Game","game"),
            Genre("Gender Bender","gender-bender"),
            Genre("Harem","harem"),
            Genre("Hentai","hentai"),
            Genre("Horror","horror"),
            Genre("Josei","josei"),
            Genre("Live action","live-action"),
            Genre("Magia","magia"),
            Genre("Manga","manga"),
            Genre("Manhua","manhua"),
            Genre("Manhwa","manhwa"),
            Genre("Mature","mature"),
            Genre("Mecha","mecha"),
            Genre("Militari","militari"),
            Genre("Mistero","mystery"),
            Genre("Musica","musica"),
            Genre("One shot","one-shot"),
            Genre("Parodia","parodia"),
            Genre("Psicologico","psychological"),
            Genre("Romantico","romance"),
            Genre("RPG","rpg"),
            Genre("Sci-fi","sci-fi"),
            Genre("Scolastico","school-life"),
            Genre("Seinen","seinen"),
            Genre("Shoujo","shoujo"),
            Genre("Shoujo Ai","shoujo-ai"),
            Genre("Shounen","shounen"),
            Genre("Shounen Ai","shounen-ai"),
            Genre("Slice of Life","slice-of-life"),
            Genre("Smut","smut"),
            Genre("Soft Yaoi","soft-yaoi"),
            Genre("Soft Yuri","soft-yuri"),
            Genre("Soprannaturale","supernatural"),
            Genre("Spazio","spazio"),
            Genre("Sport","sports"),
            Genre("Storico","historical"),
            Genre("Super Poteri","superpower"),
            Genre("Thriller","thriller"),
            Genre("Tragico","tragedy"),
            Genre("Vampiri","vampiri"),
            Genre("Webtoon","webtoon"),
            Genre("Yaoi","yaoi"),
            Genre("Yuri","yuri")
    )
    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
            Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }


}
