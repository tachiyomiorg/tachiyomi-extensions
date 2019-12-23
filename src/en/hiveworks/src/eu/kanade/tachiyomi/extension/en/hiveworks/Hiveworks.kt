package eu.kanade.tachiyomi.extension.en.hiveworks

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class HiveWorks : ParsedHttpSource() {

    override val name = "Hiveworks Comics"
    override val baseUrl = "https://hiveworkscomics.com"
    override val lang = "en"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    override fun popularMangaSelector() = "div.comicblock"
    override fun latestUpdatesSelector() = throw Exception ("Not Used")
    override fun searchMangaSelector() = popularMangaSelector()
    override fun chapterListSelector() = "select[name=comic] option"

    override fun popularMangaNextPageSelector() = "none"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
    override fun latestUpdatesRequest(page: Int) = throw Exception ("Not Used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendPath("home")
        //Append uri filters
        filters.forEach {
            if (it is UriFilter)
                it.addToUri(uri)
        }
        return GET(uri.toString(), headers)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers)
    override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)
    override fun chapterListRequest(manga: SManga): Request {
        val uri = Uri.parse(manga.url).buildUpon()
            .appendPath("comic")
            .appendPath("archive")
            .build().toString()
        return GET(uri, headers)
    }
    //override fun chapterListRequest(manga: SManga) = GET(manga.url + "/comic/archive", headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element)= mangaFromElement(element)

        private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
            manga.url = element.select("a.comiclink").first().attr("abs:href")
            Log.i("TachiDebug", "MangaURL => ${manga.url}")
            manga.title = element.select("h1").text().trim()
            manga.thumbnail_url = element.select("img").attr("abs:src")
            manga.artist = element.select("h2").text().removePrefix("by").trim()
            manga.author = manga.artist
            manga.description = element.select("div.description").text().trim() + "\n" + "\n" + "*Not all comics are supported*"
            manga.genre = element.select("div.comicrating").text().trim()
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val uri = Uri.parse(document.baseUri())
        val baseUrl = "${uri.scheme}://${uri.authority}"
        val elements = document.select(chapterListSelector())
        val chapters = mutableListOf<SChapter>()
        for (i in 1 until elements.size) {
            chapters.add(createChapter(elements[i] , baseUrl))
        }
        chapters.reverse()
        return chapters
    }

    private fun createChapter(element: Element, baseUrl: String?) = SChapter.create().apply {
        name = element.text().substringAfter("-").trim()
        url = "$baseUrl/" + element.attr("value")
        date_upload = parseDate(element.text().substringBefore("-").trim())
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US ).parse(date).time
    }

    override fun chapterFromElement(element: Element) = throw Exception("Not Used")

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img[id=cc-comic]")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }
    
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    //Filter List Code
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Text search does not work."),
        Filter.Header("Only one filter can be used at a time"),
        Filter.Separator(),
        RatingFilter(),
        GenreFilter(),
        SortFilter()
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

    private class RatingFilter: UriSelectFilter("Rating","age", arrayOf(
        Pair("all","All"),
        Pair("everyone","Everyone"),
        Pair("teen","Teen"),
        Pair("young-adult","Young Adult"),
        Pair("mature","Mature")
    ))

    private class GenreFilter: UriSelectFilter("Genre","genre", arrayOf(
        Pair("all","All"),
        Pair("action/adventure","Action/Adventure"),
        Pair("animated","Animated"),
        Pair("autobio","Autobio"),
        Pair("comedy","Comedy"),
        Pair("drama","Drama"),
        Pair("dystopian","Dystopian"),
        Pair("fairytale","Fairytale"),
        Pair("fantasy","Fantasy"),
        Pair("finished","Finished"),
        Pair("historical-fiction","Historical Fiction"),
        Pair("horror","Horror"),
        Pair("lgbt","LGBT"),
        Pair("mystery","Mystery"),
        Pair("romance","Romance"),
        Pair("sci-fi","Science Fiction"),
        Pair("slice-of-life","Slice of Life"),
        Pair("steampunk","Steampunk"),
        Pair("superhero","Superhero"),
        Pair("urban-fantasy","Urban Fantasy")
        ))

    private class SortFilter: UriSelectFilter("Sort By","sortby", arrayOf(
        Pair("none","None"),
        Pair("a-z","A-Z"),
        Pair("z-a","Z-A")
    ))



}


