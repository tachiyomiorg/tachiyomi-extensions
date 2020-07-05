package eu.kanade.tachiyomi.extension.all.multporn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.util.Locale
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

open class MultPorn(
    override val lang: String
) : ParsedHttpSource() {
    override val name = "MultPorn"
    final override val baseUrl = "https://multporn.net"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/new?page=${page - 1}", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/new?page=${page - 1}", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search?search_api_views_fulltext=$query&type=All&sort_by=search_api_relevance&page=${page - 1}")?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun popularMangaSelector() = ".masonry-item"
    override fun searchMangaSelector() = popularMangaSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun chapterListSelector() = ".chapters > li"

    override fun popularMangaNextPageSelector() = ".pager-next"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = getMangaUrl(element)
        manga.title = getMangaTitle(element)
        manga.thumbnail_url = getMangaThumbUrl(element)
        return manga
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/75.0")
        .add("Referer", baseUrl)

    open fun getMangaUrl(element: Element): String = "$baseUrl/${element.select("a").attr("href")}"
    open fun getMangaTitle(element: Element) = element.select("a").first().text().trim()
    open fun getMangaThumbUrl(element: Element): String = element.select("img").attr("src")

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = "$baseUrl${element.select("a").attr("href")}"
        manga.title = element.select("a").first().text().trim()
        manga.thumbnail_url = getMangaThumbUrl(element)
        return manga
    }

    private fun searchMangaByIdRequest(id: String, page: Int) = GET("$baseUrl/comics/$id?page=0%2C$page", headers)
    private fun searchMangaByCategoryRequest(category: String, page: Int) = GET("$baseUrl/category/$category?page=0%2C$page", headers)

    private fun searchMangaByTagsParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val filteredList = document.select(".view-id-product").select("td").filter { it.select(".views-field-title").first() !== null }
        return MangasPage(filteredList.map { searchMangaFromElement(it) }.toList(),
            document.select(popularMangaNextPageSelector()).first() != null)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith("Section: ") -> {
                val category = query.removePrefix("Section: ").toLowerCase(Locale.ROOT)
                    .replace("\\s".toRegex(), "_").replace("[^a-z0-9_]".toRegex(), "")
                client.newCall(searchMangaByIdRequest(category, page - 1))
                    .asObservableSuccess()
                    .map { response -> searchMangaByTagsParse(response) }
            }
            query.startsWith("Genre: ") -> {
                val id = query.removePrefix("Genre: ").toLowerCase(Locale.ROOT)
                    .replace("\\s".toRegex(), "_").replace("[^a-z0-9_]".toRegex(), "")
                client.newCall(searchMangaByCategoryRequest(id, page - 1))
                    .asObservableSuccess()
                    .map { response -> searchMangaByTagsParse(response) }
            }
            else -> super.fetchSearchManga(page - 1, query, filters)
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = getMangaUrl(element)
        chapter.chapter_number = "0".toFloat()
        chapter.name = getMangaTitle(element)
        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterNumber = 1
        val document = response.asJsoup()
/*
        TODO: Support for multi-part/chapter comics
        NOTE: Multporn has very shoddy support for multi-part/chapter comics. It's divided among various
        methods like putting all chapters in same section and for some having next/previous part
        links and others. Even in that it sometimes works and sometimes does not,
         so we are leaving this for future
        val chapterList = mutableListOf<SChapter>()
        do {
            if (chapterNumber > 1) {
                document = client.newCall(GET("$baseUrl${document.select(".field-type-text-with-summary").select("a").attr("href")}", headers)).execute().asJsoup()
            }
            chapterList.add(chapterParse(document, chapterNumber++))
        } while (document.select(".field-type-text-with-summary").select("a").text().trim() == "Next Part")*/
        return listOf(chapterParse(document, chapterNumber))
    }

    /*Suppressed the warning because this is intended to change in future. Look at the commented code in chapterListParse*/
    @Suppress("SameParameterValue")
    private fun chapterParse(document: Document, index: Int): SChapter {
        val chapter = SChapter.create()
        chapter.name = document.select("meta[property='og:title']").attr("content").trim()
        chapter.url = document.select("meta[property='og:url']").attr("content").trim()
        chapter.chapter_number = index.toFloat()
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select(".field-name-field-author").select("a").text().trim()
        manga.artist = manga.author
        manga.description = ""
        manga.thumbnail_url = document.select("meta[property='og:image']").attr("content").toString()
        val sections = document.select(".field-name-field-com-group>ul>li>a").map { it.text() }
        val genreList = document.select(".field-name-field-category>ul>li>a").map { "Genre: ${it.text()}" }.toMutableList()
        sections.forEach { genreList.add("Section: $it") }
        manga.genre = genreList.joinToString(", ")
        var status = SManga.COMPLETED
        for (section in sections) {
            if (section.toLowerCase(Locale.ROOT) == "ongoings") {
                status = SManga.ONGOING
                break
            }
        }
        manga.status = status
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imgContainer = if (document.select(".field-name-field-com-pages").first() != null) {
            document.select(".field-name-field-com-pages")
        } else {
            document.select(".jb-image")
        }
        return imgContainer.select("img").mapIndexed { i, img ->
            Page(i, "", img.attr("src"))
        }
    }

    override fun pageListParse(document: Document) = throw Exception("Not used")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")
}
