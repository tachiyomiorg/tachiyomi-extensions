package eu.kanade.tachiyomi.extension.en.mangakisa

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit


class MangaKisa : ParsedHttpSource() {

    override val name = "MangaKisa"
    override val baseUrl = "https://mangakisa.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    override fun popularMangaSelector() = ".an"
    override fun latestUpdatesSelector() = ".episode-box-2"
    override fun searchMangaSelector() = "div.iepbox a.an"
    override fun chapterListSelector() = ".infoepbox > a"

    override fun popularMangaNextPageSelector() = "div:containsOwn(Next Page >)"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        val page0 = page-1
        return GET("$baseUrl/popular/$page0", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        val page0 = page-1
        return GET("$baseUrl/all-updates/latest/$page0", headers)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val page0 = page-1
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search?q=$query").buildUpon()
        } else {
            val uri = Uri.parse("$baseUrl/").buildUpon()
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.appendPath("$page0")
        }
        return GET(uri.toString(), headers)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element)= mangaFromElement(element)
        private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select(".an").first().attr("href"))
        manga.title = element.select("img").attr("alt").trim()
        manga.thumbnail_url = baseUrl + element.select("img").attr("src")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/" + element.select("a").attr("href"))
        chapter.chapter_number = element.select("[class*=infoept2] > div").text().toFloat()
        chapter.name = "Chapter " + element.select("[class*=infoept2] > div").text().trim()
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".infopicbox > img").attr("alt").trim()
        manga.artist = document.select(".textc > a[href*=authors]").text().trim()
        manga.author = document.select(".textc > a[href*=authors]").text().trim()
        manga.description = document.select(".infodes2").first().text()
        val glist = document.select("a.infoan[href*=genres]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select(".textc:contains(Ongoing), .textc:contains(Completed)")?.first()?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.thumbnail_url = baseUrl + "/" +  document.select(".infopicbox > img").attr("src")
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter()
    )

    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                           val firstIsUnspecified: Boolean = true,
                                           defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendPath(uriParam)
                    .appendPath(vals[state].first)
        }
    }

    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    private class GenreFilter : UriSelectFilter("Genre","genres", arrayOf(
        Pair("all", "ALL"),
        Pair("action", "Action "),
        Pair("adult", "Adult "),
        Pair("adventure", "Adventure "),
        Pair("comedy", "Comedy "),
        Pair("cooking", "Cooking "),
        Pair("doujinshi", "Doujinshi "),
        Pair("drama", "Drama "),
        Pair("ecchi", "Ecchi "),
        Pair("fantasy", "Fantasy "),
        Pair("gender-bender", "Gender Bender "),
        Pair("harem", "Harem "),
        Pair("historical", "Historical "),
        Pair("horror", "Horror "),
        Pair("isekai", "Isekai "),
        Pair("josei", "Josei "),
        Pair("manhua", "Manhua "),
        Pair("manhwa", "Manhwa "),
        Pair("martial-arts", "Martial Arts "),
        Pair("mature", "Mature "),
        Pair("mecha", "Mecha "),
        Pair("medical", "Medical "),
        Pair("mystery", "Mystery "),
        Pair("one-shot", "One Shot "),
        Pair("psychological", "Psychological "),
        Pair("romance", "Romance "),
        Pair("school-life", "School Life "),
        Pair("sci-fi", "Sci Fi "),
        Pair("seinen", "Seinen "),
        Pair("shoujo", "Shoujo "),
        Pair("shoujo-ai", "Shoujo Ai "),
        Pair("shounen", "Shounen "),
        Pair("shounen-ai", "Shounen Ai "),
        Pair("slice-of-life", "Slice Of Life "),
        Pair("smut", "Smut "),
        Pair("sports", "Sports "),
        Pair("supernatural", "Supernatural "),
        Pair("tragedy", "Tragedy "),
        Pair("webtoons", "Webtoons "),
        Pair("yaoi", "Yaoi "),
        Pair("yuri", "Yuri ")
        ))


}

