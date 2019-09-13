package eu.kanade.tachiyomi.extension.zh.pufei

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.UnsupportedOperationException
import com.squareup.duktape.Duktape
import android.util.Base64

// temp patch:
// https://github.com/inorichi/tachiyomi/pull/2031

import org.jsoup.Jsoup // import for patch

fun asJsoup(response: Response, html: String? = null): Document {
    return Jsoup.parse(html ?: bodyWithAutoCharset(response), response.request().url().toString())
}

fun bodyWithAutoCharset(response: Response, _charset: String? = null): String {
    val htmlBytes: ByteArray = response.body()!!.bytes()
    var c = _charset

    if (c == null) {
        var regexPat = Regex("""charset=(\w+)""")
        val match = regexPat.find(String(htmlBytes))
        c = match?.groups?.get(1)?.value
    }

    return String(htmlBytes, charset(c ?: "utf8"))
}

// patch finish

fun ByteArray.toHexString() = joinToString("%") { "%02x".format(it) }

class Pufei : ParsedHttpSource() {

    override val name = "扑飞漫画"
    override val baseUrl = "http://m.pufei.net"
    override val lang = "zh"
    override val supportsLatest = true
    val imageServer = "http://res.img.pufei.net/"

    override fun popularMangaSelector() = "ul#detail li"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun headersBuilder() = super.headersBuilder()
            .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhua/paihang.html", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhua/update.html", headers)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.select("h3").text().trim()
        }
        return manga
    }

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.book-detail")

        val manga = SManga.create()
        manga.description = infoElement.select("div#bookIntro > p").text().trim()
        manga.thumbnail_url = infoElement.select("div.thumb > img").first()?.attr("src")
//        manga.author = infoElement.select("dd").first()?.text()
        return manga
    }

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaSelector() = "ul#detail > li"

    private fun encodeGBK(str: String) = "%" + str.toByteArray(charset("gb2312")).toHexString()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/e/search/?searchget=1&tbname=mh&show=title,player,playadmin,bieming,pinyin,playadmin&tempid=4&keyboard=" + encodeGBK(query))?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
//        val document = response.asJsoup()
        val document = asJsoup(response)
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        return MangasPage(mangas, false)
    }

    override fun chapterListSelector() = "div.chapter-list > ul > li"

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().trim()
        return chapter
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val re = Regex("cp=\"(.*?)\"")
        val imgbase64 = re.find(html)?.groups?.get(1)?.value
        val imgCode = String(Base64.decode(imgbase64, Base64.DEFAULT))
        val imgArrStr = Duktape.create().use {
            it.evaluate(imgCode + """.join('|')""") as String
        }
        return imgArrStr.split('|').mapIndexed { i, imgStr ->
            Page(i, "", imageServer + imgStr)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    override fun getFilterList() = FilterList(
            GenreFilter(getGenreList())
    )

    private fun getGenreList() = arrayOf(
            "All"
    )

    // temp patch
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = asJsoup(response)

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = asJsoup(response)

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return mangaDetailsParse(asJsoup(response))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = asJsoup(response)
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun pageListParse(response: Response): List<Page> {
        return pageListParse(asJsoup(response))
    }

    override fun imageUrlParse(response: Response): String {
        return imageUrlParse(asJsoup(response))
    }
    // patch finish
}

