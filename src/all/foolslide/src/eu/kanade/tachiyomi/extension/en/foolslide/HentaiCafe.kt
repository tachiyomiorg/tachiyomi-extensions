package eu.kanade.tachiyomi.extension.all.foolslide

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder

class HentaiCafe : FoolSlide("Hentai Cafe", "https://hentai.cafe", "en", "/manga") {
    // We have custom latest updates logic so do not dedupe latest updates
    override val dedupeLatestUpdates = false

    // Does not support popular manga
    override fun fetchPopularManga(page: Int) = fetchLatestUpdates(page)

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        val urlElement = element.select(".entry-thumb").first()
        setUrlWithoutDomain(urlElement.attr("href"))
        thumbnail_url = urlElement.child(0).attr("src")
        title = element.select(".entry-title").text().trim()
    }

    override fun latestUpdatesNextPageSelector() = ".x-pagination li:last-child a"

    override fun latestUpdatesRequest(page: Int) = pagedRequest("$baseUrl/", page)

    override fun latestUpdatesSelector() = "article"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(".entry-title").text()
        val contentElement = document.select(".entry-content").first()
        thumbnail_url = contentElement.child(0).child(0).attr("src")

        fun filterableTagsOfType(type: String) = contentElement.select("a")
                .filter { "$baseUrl/$type/" in it.attr("href") }
                .joinToString { it.text() }

        genre = filterableTagsOfType("tag")
        artist = filterableTagsOfType("artist")
    }

    // Note that the reader URL cannot be deduced from the manga URL all the time which is why
    //   we still need to parse the manga info page
    // Example: https://hentai.cafe/aiya-youngest-daughters-circumstances/
    override fun chapterListParse(response: Response) = listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(response.asJsoup().select("[title=Read]").attr("href"))
                name = "Chapter"
                chapter_number = 0.0f
            }
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url: String? = null
        var queryString: String? = null
        fun requireNoUrl() = require(url == null && queryString == null) {
            "You cannot combine filters or use text search with filters!"
        }

        filters.findInstance<ArtistFilter>()?.let { f ->
            if(f.state.isNotBlank()) {
                requireNoUrl()
                url = "/artist/${f.state
                        .trim()
                        .toLowerCase()
                        .replace(ARTIST_INVALID_CHAR_REGEX, "-")}/"
            }
        }
        filters.findInstance<BookFilter>()?.let { f ->
            if(f.state) {
                requireNoUrl()
                url = "/category/book/"
            }
        }
        filters.findInstance<TagFilter>()?.let { f ->
            if(f.state != 0) {
                requireNoUrl()
                url = "/tag/${f.values[f.state].name}/"
            }
        }

        if(query.isNotBlank()) {
            requireNoUrl()
            url = "/"
            queryString = "s=" + URLEncoder.encode(query, "UTF-8")
        }

        return url?.let {
            pagedRequest("$baseUrl$url", page, queryString)
        } ?: latestUpdatesRequest(page)
    }

    private fun pagedRequest(url: String, page: Int, queryString: String? = null): Request {
        // The site redirects page 1 -> url-without-page so we do this redirect early for optimization
        val builtUrl =  if(page == 1) url else "${url}page/$page/"
        return GET(if(queryString != null) "$builtUrl?$queryString" else builtUrl)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
                .asObservable().doOnNext { response ->
                    if(!response.isSuccessful) {
                        response.close()
                        // Better error message for invalid artist
                        if (response.code() == 404
                                && !filters.findInstance<ArtistFilter>()?.state.isNullOrBlank())
                            error("Invalid artist!")
                        else throw Exception("HTTP error ${response.code()}")
                    }
                }
                .map { response ->
                    searchMangaParse(response)
                }
    }

    override fun getFilterList() = FilterList(
            Filter.Header("Filters cannot be used while searching."),
            Filter.Header("Only one filter may be used at a time."),
            Filter.Separator(),
            ArtistFilter(),
            BookFilter(),
            TagFilter()
    )

    class ArtistFilter : Filter.Text("Artist (must be exact match)")
    class BookFilter : Filter.CheckBox("Show books only", false)
    class TagFilter : Filter.Select<Tag>("Tag", arrayOf(
            Tag("all", "All"),
            Tag("ahegao", "Ahegao"),
            Tag("anal", "Anal"),
            Tag("big-ass", "Big ass"),
            Tag("big-breast", "Big breast"),
            Tag("big-dick", "Big dick"),
            Tag("bondage", "Bondage"),
            Tag("cheating", "Cheating"),
            Tag("chubby", "Chubby"),
            Tag("color", "Color"),
            Tag("condom", "Condom"),
            Tag("cosplay", "Cosplay"),
            Tag("cunnilingus", "Cunnilingus"),
            Tag("dark-skin", "Dark skin"),
            Tag("exhibitionism", "Exhibitionism"),
            Tag("fellatio", "Fellatio"),
            Tag("femdom", "Femdom"),
            Tag("flat-chest", "Flat chest"),
            Tag("full-color", "Full color"),
            Tag("glasses", "Glasses"),
            Tag("group", "Group"),
            Tag("hairy", "Hairy"),
            Tag("handjob", "Handjob"),
            Tag("housewife", "Housewife"),
            Tag("incest", "Incest"),
            Tag("large-breast", "Large breast"),
            Tag("lingerie", "Lingerie"),
            Tag("loli", "Loli"),
            Tag("masturbation", "Masturbation"),
            Tag("nakadashi", "Nakadashi"),
            Tag("osananajimi", "Osananajimi"),
            Tag("paizuri", "Paizuri"),
            Tag("pettanko", "Pettanko"),
            Tag("rape", "Rape"),
            Tag("schoolgirl", "Schoolgirl"),
            Tag("sex-toys", "Sex Toys"),
            Tag("shota", "Shota"),
            Tag("socks", "Socks"),
            Tag("stocking", "Stocking"),
            Tag("stockings", "Stockings"),
            Tag("swimsuit", "Swimsuit"),
            Tag("teacher", "Teacher"),
            Tag("tsundere", "Tsundere"),
            Tag("uncensored", "uncensored"),
            Tag("vanilla", "Vanilla"),
            Tag("x-ray", "X-ray")
    ))
    class Tag(val name: String, val displayName: String) {
        override fun toString() = displayName
    }

    companion object {
        // Do not include dashes in this regex, this way we can deduplicate dashes
        private val ARTIST_INVALID_CHAR_REGEX = Regex("[^a-zA-Z0-9]+")
    }
}

private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T