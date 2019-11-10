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
    
    override fun popularMangaSelector() = "div.pic_box"
    override fun latestUpdatesSelector() = throw Exception("Not used")
    override fun searchMangaSelector() = popularMangaSelector()
    override fun chapterListSelector() = "div.f_left > a"

    override fun popularMangaNextPageSelector() = "a:containsOwn(後頁)"
    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/albums-index-page-$page.html", headers)

    }
    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/albums-index-page-$page-sname-$query.html", headers)
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
        //create one chapter since it is single books
        chapters.add(createChapter("1", document.baseUri()))
        return chapters
    }

    private fun createChapter(pageNumber: String, mangaUrl: String): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(mangaUrl)
        chapter.name = "Ch. $pageNumber"
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

    override fun pageListParse(document: Document): List<Page> {
        val regex = "\\/\\/\\S*(jpg)".toRegex()
        val slideaid = client.newCall(GET( baseUrl + document.select("a.btn:containsOwn(下拉閱讀)").attr("href"), headers)).execute().asJsoup()
        val galleryaid = client.newCall(GET( baseUrl + slideaid.select("script[src$=html]").attr("src"), headers)).execute().asJsoup().toString()
        val matchresult = regex.findAll(galleryaid).map { it.value }.toList()
        val pages = mutableListOf<Page>()
        for (i in 0 until matchresult.size){
            pages.add(Page(i, "", "http:"+matchresult[i]))
        }
        return pages
    }
    override fun chapterFromElement(element: Element) = throw Exception("Not used")
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")


}
