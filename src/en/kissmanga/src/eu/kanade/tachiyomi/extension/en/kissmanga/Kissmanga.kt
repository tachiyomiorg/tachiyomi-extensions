package eu.kanade.tachiyomi.extension.en.kissmanga

import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Kissmanga : ParsedHttpSource() {

    override val id: Long = 4

    override val name = "Kissmanga"

    override val baseUrl = "https://kissmanga.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = clientBuilder()

    private fun clientBuilder(): OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val originalCookies = chain.request().header("Cookie") ?: ""
            val newReq = chain
                .request()
                .newBuilder()
                .header("Cookie", "$originalCookies; ${cookiesHeader()}")
                .build()
            chain.proceed(newReq)
        }.build()!!

    private fun cookiesHeader(): String {
        val cookies = mutableMapOf<String, String>()
        cookies["fullListMode"] = "true"
        return buildCookies(cookies)
    }

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/60")
    }


    private val cssSelector = "div.bigBarContainer .barContent div:not(.arrow-general):not(.alphabet):not(.clear) div:not(.clear2):gt(10)"
    override fun popularMangaSelector() = cssSelector//"table.listing tr:gt(1)"
    override fun latestUpdatesSelector() = cssSelector//"table.listing tr:gt(1)"
    override fun searchMangaSelector() = cssSelector//popularMangaSelector()


    private val directoryNextPageSelector = "ul.pager > li > a:contains(Next)"
    override fun popularMangaNextPageSelector() = directoryNextPageSelector//"li > a:contains(› Next)"
    override fun latestUpdatesNextPageSelector() = directoryNextPageSelector//"ul.pager > li > a:contains(Next)"
    override fun searchMangaNextPageSelector() = null


    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/MangaList/MostPopular?page=$page", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/MangaList/LatestUpdate?page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("mangaName", query)

            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is Author -> add("authorArtist", filter.state)
                    is Status -> add("status", arrayOf("", "Completed", "Ongoing")[filter.state])
                    is GenreList -> filter.state.forEach { genre -> add("genres", genre.state.toString()) }
                }
            }
        }
        return POST("$baseUrl/AdvanceSearch", headers, form.build())
    }


    override fun popularMangaFromElement(element: Element): SManga = throw Exception("when using fetch... this functions dont run")
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("when using fetch... this functions dont run")
    override fun searchMangaFromElement(element: Element): SManga = throw Exception("when using fetch... this functions dont run")


    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                getMangaList(response)
            }
    }
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                getMangaList(response)
            }
    }
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                getMangaList(response)
            }
    }

    private fun getMangaList(response: Response): MangasPage {
        val responseData = response.asJsoup()
        response.close()

        val nextPage = responseData.select(directoryNextPageSelector).hasText()
        val results = responseData.select(cssSelector)

        return MangasPage(parseSearch(results), nextPage)
    }


    private fun parseSearch(elements: Elements): List<SManga> {
        val mutableList = mutableListOf<SManga>()

        elements.forEach {
            val manga = SManga.create()

            manga.title = it.select(".title").text().trim()

            //i dont think this is needed anymore, leaving it just in case
            if (manga.title.contains("[email protected]", true)) {//check if cloudfire email obfuscation is affecting title name
                // I just put this here so when I'm testing, I'll see if this actually happens, I never saw this get run...
                // Log.e("kissEmailProtected", manga.title)
                try {
                    var str: String = it.html()
                    //get the  number
                    str = str.substringAfter("data-cfemail=\"")
                    str = str.substringBefore("\">[email")
                    val sb = StringBuilder()
                    //convert number to char
                    val r = Integer.valueOf(str.substring(0, 2), 16)!!
                    var i = 2
                    while (i < str.length) {
                        val c = (Integer.valueOf(str.substring(i, i + 2), 16) xor r).toChar()
                        sb.append(c)
                        i += 2
                    }
                    //replace the new word into the title
                    manga.title = manga.title.replace("[email protected]", sb.toString(), true)
                } catch (e: Exception) {
                    //on error just default to obfuscated title
                    manga.title = it.select(".title").text().trim()
                }
            }
            manga.setUrlWithoutDomain(it.select("a").first().attr("href"))
            manga.thumbnail_url = it.select("a img").attr("src")

            manga.genre = it.select("p:has(span:contains(Genres:)) > *:gt(0)").text()
            manga.description = it.select("p:has(span:contains(Summary:)) ~ p").text()
            manga.status = it.select("p:has(span:contains(Status:))").first()?.text().orEmpty().let { parseStatus(it) }

//            manga.author = //isn't available here on Kissmanga at this step
//            manga.artist = //Kissmanga doesn't have artist

            //still not sure what this does
            manga.initialized = true
            mutableList.add(manga)
        }
        return mutableList
    }


    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.barContent").first()

        val manga = SManga.create()
        manga.author = infoElement.select("p:has(span:contains(Author:)) > a").first()?.text()
        manga.genre = infoElement.select("p:has(span:contains(Genres:)) > *:gt(0)").text()
        manga.description = infoElement.select("p:has(span:contains(Summary:)) ~ p").text()
        manga.status = infoElement.select("p:has(span:contains(Status:))").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select(".rightBox:eq(0) img").first()?.attr("src")
        return manga
    }

    fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.listing tr:gt(1)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("td:eq(1)").first()?.text()?.let {
            SimpleDateFormat("MM/dd/yyyy").parse(it).time
        } ?: 0
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body()!!.string()

        val pages = mutableListOf<Page>()

        // Kissmanga now encrypts the urls, so we need to execute these two scripts in JS.
        val ca = client.newCall(GET("$baseUrl/Scripts/ca.js", headers)).execute().body()!!.string()
        val lo = client.newCall(GET("$baseUrl/Scripts/lo.js", headers)).execute().body()!!.string()

        Duktape.create().use {
            it.evaluate(ca)
            it.evaluate(lo)

            // There are two functions in an inline script needed to decrypt the urls. We find and
            // execute them.
            var p = Pattern.compile("(var.*CryptoJS.*)")
            var m = p.matcher(body)
            while (m.find()) {
                it.evaluate(m.group(1))
            }

            // Finally find all the urls and decrypt them in JS.
            p = Pattern.compile("""lstImages.push\((.*)\);""")
            m = p.matcher(body)

            var i = 0
            while (m.find()) {
                val url = it.evaluate(m.group(1)) as String
                pages.add(Page(i++, "", url))
            }
        }

        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    private class Status : Filter.TriState("Completed")
    private class Author : Filter.Text("Author")
    private class Genre(name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        Author(),
        Status(),
        GenreList(getGenreList())
    )

    // $("select[name=\"genres\"]").map((i,el) => `Genre("${$(el).next().text().trim()}", ${i})`).get().join(',\n')
    // on https://kissmanga.com/AdvanceSearch
    private fun getGenreList() = listOf(
        Genre("4-Koma"),
        Genre("Action"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Comedy"),
        Genre("Comic"),
        Genre("Cooking"),
        Genre("Doujinshi"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender Bender"),
        Genre("Harem"),
        Genre("Historical"),
        Genre("Horror"),
        Genre("Isekai"),
        Genre("Josei"),
        Genre("Lolicon"),
        Genre("Manga"),
        Genre("Manhua"),
        Genre("Manhwa"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Medical"),
        Genre("Music"),
        Genre("Mystery"),
        Genre("One shot"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci-fi"),
        Genre("Seinen"),
        Genre("Shotacon"),
        Genre("Shoujo"),
        Genre("Shoujo Ai"),
        Genre("Shounen"),
        Genre("Shounen Ai"),
        Genre("Slice of Life"),
        Genre("Smut"),
        Genre("Sports"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Webtoon"),
        Genre("Yaoi"),
        Genre("Yuri")
    )
}
