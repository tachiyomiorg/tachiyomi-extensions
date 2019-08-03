package eu.kanade.tachiyomi.extension.es.heavenmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class HeavenManga : ParsedHttpSource() {

    override val name = "HeavenManga"

    override val baseUrl = "http://heavenmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/60")
    }

    private fun getBuilder(url: String): String {
        val req = Request.Builder()
            .headers(headersBuilder()
//                .add("Referer", "$baseUrl/library/manga/")
                .add("Cache-mode", "no-cache")
                .build())
            .url(url)
            .build()

        return client.newCall(req)
            .execute()
            .request()
            .url()
            .toString()
    }


    override fun popularMangaSelector() = ".top.clearfix .ranking"

    override fun latestUpdatesSelector() = "#container .ultimos_epis .not"

    private fun latestUpdatesListSelector() = "#container .ultimos_epis .not"

    override fun searchMangaSelector() = ".top.clearfix .cont_manga"

    override fun chapterListSelector() = "#mamain ul li"

    private fun chapterPageSelector() = "a#l"

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()


    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
        = GET("$baseUrl/buscar/$query.html", headers)

    override fun imageUrlRequest(page: Page) = GET(page.url, headers)

//    override fun pageListRequest(chapter: SChapter): Request {
//        val url = getBuilder(chapter.url)
//        val request: Request = Request.Builder()
//            .url(url)
//            .build()
//        val response: Response = client.newCall(request).execute()
//        val element = response.asJsoup()
//        val newUrl = element.select(chapterPageSelector()).attr("href")
//
//        return GET(newUrl, headers)
//    }

    // get contents of a url
    private fun getUrlContents(url: String): Document = Jsoup.connect(url).timeout(0).get()




    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            val allElements: Elements = it.select(".box .tit")
            //get all elements under .box .tit
            for (e: Element in allElements) {
                title = e.childNode(0).toString() //the title
            }
            thumbnail_url = it.select(".box img").attr("src")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            val latestChapter = getUrlContents(it.attr("href"))
            val url = latestChapter.select(".rpwe-clearfix:last-child a")
            setUrlWithoutDomain(url.attr("href"))
            title = it.select("span span").text()
            thumbnail_url = it.select("img").attr("src")
        }
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select("a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("header").text()
            thumbnail_url = it.select("img").attr("src")
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val timeElement = element.select("span").first()
        val time = timeElement.text()
        val date = time.replace("--", "-")
        val url = urlElement.attr("href")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(url)//chapterUrl(url))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(date.toString())
        return chapter
    }

    private fun chapterUrl(pageUrl: String): String {
        val element = getUrlContents(pageUrl)
        return element.select(chapterPageSelector()).attr("href")
    }


    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        document.select(".left.home").let {
            val genres = it.select(".sinopsis a")?.map {
                it.text()
            }

            genre = genres?.joinToString(", ")
            val allElements: Elements = document.select(".sinopsis")
            //get all elements under .sinopsis
            for (e: Element in allElements) {
                description = e.childNode(0).toString() //the description
            }
        }

        thumbnail_url = document.select(".cover.clearfix img[style='width:142px;height:212px;']").attr("src")
    }

    private fun parseChapterDate(date: String): Long = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(date).time

    override fun chapterListParse(response: Response): List<SChapter> {
        var response = response
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()
        document.select(chapterListSelector()).forEach {
            chapters.add(chapterFromElement(it))
        }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val body = response.asJsoup()
        val imgUrl = body.select("#p").first().attr("src")

        body.select(".chaptercontrols > select").first().getElementsByTag("option").forEach {
            add(Page(size, it.attr("value"), "$imgUrl"))
        }
    }


    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun pageListParse(document: Document) = throw UnsupportedOperationException("Not used")

//    override fun pageListParse(document: Document): List<Page> {
//        val pages = mutableListOf<Page>()
//        document.select(".chaptercontrols > select").first().getElementsByTag("option").forEach {
//            pages.add(Page(pages.size, it.attr("value")))
//        }
//        pages.getOrNull(0)?.imageUrl = imageUrlParse(document)
//        return pages
//    }

    // TODO: learn how to deal with filtering genres
    private class Genre(name: String,  val id: String = name.replace(' ', '+')) : Filter.CheckBox(name)

    override fun getFilterList() = FilterList()

    // Array.from(document.querySelectorAll('.categorias a font font')).map(a => `Genre("${a.textContent}")`).join(',\n')
    // on http://heavenmanga.com/top/
    private fun getGenreList() = listOf(
        Genre("Action "),
        Genre("Adult "),
        Genre("Adventure "),
        Genre("Martial Arts "),
        Genre("acontesimientos of Life "),
        Genre("Bakunyuu "),
        Genre("Sci-fi "),
        Genre("Comic "),
        Genre("Combat "),
        Genre("Comedy "),
        Genre("Cooking "),
        Genre("Cotidiano "),
        Genre("Schoolgirls "),
        Genre("critical social "),
        Genre("science fiction "),
        Genre("gender change "),
        Genre("things in life "),
        Genre("Drama "),
        Genre("Sport "),
        Genre("Doujinshi "),
        Genre("Offender "),
        Genre("Ecchi "),
        Genre("School "),
        Genre("Erotico "),
        Genre("School "),
        Genre(" Lifestyle "),
        Genre("Fantasia "),
        Genre("Fragments of Life "),
        Genre("Gore "),
        Genre("Gender Bender "),
        Genre("Humor "),
        Genre("Harem "),
        Genre("Haren "),
        Genre("Hentai "),
        Genre("Horror "),
        Genre("Psychological "),
        Genre("Romance "),
        Genre("Life Counts "),
        Genre("Smut "),
        Genre("Shojo "),
        Genre("Shonen "),
        Genre("Seinen "),
        Genre("Shoujo "),
        Genre("Shounen "),
        Genre("Suspense "),
        Genre("School Life "),
        Genre("Supernatural"),
        Genre(" SuperHeroes "),
        Genre("Supernatural "),
        Genre("Slice of Life "),
        Genre("Super Powers "),
        Genre("Terror "),
        Genre("Tournament "),
        Genre("Tragedy "),
        Genre("Transexual "),
        Genre("Life "),
        Genre("Vampires "),
        Genre("Violence "),
        Genre("Past Life "),
        Genre("Daily Life "),
        Genre("Life of school"),
        Genre("Webtoon "),
        Genre("Webtoons "),
        Genre("Yaoi "),
        Genre("Yuri")
    )
}
