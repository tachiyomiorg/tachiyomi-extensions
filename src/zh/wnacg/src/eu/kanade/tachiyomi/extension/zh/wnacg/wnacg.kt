package eu.kanade.tachiyomi.extension.zh.wnacg

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


class wnacg : ParsedHttpSource() {
    override val name = "紳士漫畫"
    override val baseUrl = "https://www.wnacg.org"
    override val lang = "zh"
    override val supportsLatest = false
    /*
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!
    */
    override fun popularMangaSelector() = "div.pic_box"
    override fun latestUpdatesSelector() = throw Exception("Not used")
    override fun searchMangaSelector() = "div.iepbox a.an"
    override fun chapterListSelector() = "div.f_left > a"

    override fun popularMangaNextPageSelector() = "a:containsOwn(後頁)"
    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/albums-index-page-$page.html", headers)

    }
    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/albums-index-page-$page-sname-$query.html")//.buildUpon()
        return GET(uri.toString(), headers)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")
    override fun searchMangaFromElement(element: Element)= mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first().attr("href"))
        manga.title = element.select("a").attr("title").trim()
        manga.thumbnail_url = "https:" + element.select("img").attr("src")
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        //create first chapter since its on main manga page
        chapters.add(createChapter("1", document.baseUri()))
        //see if there are multiple chapters or not
        do {
            val newpage = client.newCall(GET( baseUrl + document.select("a:containsOwn(後頁)").attr("href"), headers)).execute().asJsoup()
            chapters.add(createChapter(newpage.select("span.thispage").text(), newpage.baseUri()))
            document = newpage
        } while (!document.select("a:containsOwn(後頁)").isNullOrEmpty())
        /*
        document.select(chapterListSelector())?.let { it ->
            it.forEach {
                if (!it.text().contains("後頁", true)) {
                    val url = it.attr("href")
                    chapters.add(createChapter(it.text(), url))
                }
            }
        }
        */
        chapters.reverse()

        return chapters
    }

    private fun createChapter(pageNumber: String, mangaUrl: String): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(mangaUrl)
        chapter.name = "Page $pageNumber"
        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h2")?.text()?.trim() ?:"Unknown"
        manga.artist = document.select("div.uwuinfo p")?.first()?.text()?.trim() ?:"Unknown"
        manga.author = document.select("div.uwuinfo p")?.first()?.text()?.trim() ?:"Unknown"
        //val glist = document.select("a.tagshow").map { it?.text() }
        //manga.genre = glist.joinToString(", ")
        manga.thumbnail_url = "$baseUrl/" +  document.select("div.uwthumb img").first().attr("src")
        return manga
    }

    /*
        override fun pageListParse(response: Response): List<Page> {
            val body = response.asJsoup()
            val pages = mutableListOf<Page>()
            val elements = body.select("img")
            for (i in 0 until elements.size) {
                pages.add(Page(i, "", getImage(elements[i])))
            }
            return pages
        }

        private fun getImage(element: Element): String {
            var url =
                when {
                    element.attr("data-src").endsWith(".jpg") || element.attr("data-src").endsWith(".png") || element.attr("data-src").endsWith(".jpeg") -> element.attr("data-src")
                    element.attr("src").endsWith(".jpg") || element.attr("src").endsWith(".png") || element.attr("src").endsWith(".jpeg") -> element.attr("src")
                    else -> element.attr("src")
                }
            if (url.startsWith("//")) {
                url = "http:$url"
            }
            return url
        }
    */
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div.pic_box")?.forEach {
            val imgdoc = client.newCall(GET( baseUrl + it.select("a").attr("href"), headers)).execute().asJsoup()
            val imgurl = imgdoc.select("img[id=picarea]").attr("src")
            pages.add(Page(pages.size, "", "https:$imgurl"))
        }
        return pages
    }
    override fun chapterFromElement(element: Element) = throw Exception("Not used")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")


}
