package eu.kanade.tachiyomi.extension.zh.hanhankuman

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HanhanKuman : ParsedHttpSource() {

    override val name = "汗汗酷漫"
    override val baseUrl = "http://www.hhimm.com"
    override val lang = "zh"
    override val supportsLatest = true

    override fun popularMangaSelector() = ".cTopComicList > div.cComicItem"
    override fun searchMangaSelector() = ".cComicList > li"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun chapterListSelector() = "ul.cVolUl > li"

    override fun searchMangaNextPageSelector() = "li.next"
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/hotrating.aspx", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/top/newrating.aspx", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/comic/?act=search&st=$query")?.newBuilder()
        return GET(url.toString(), headers)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.url = element.select("a").first()!!.attr("href")
        manga.title = element.select("span.cComicTitle").text().trim()
        manga.author = element.select("span.cComicAuthor").first()?.text()?.trim()
        manga.thumbnail_url = element.select("div.cListSlt > a > img").attr("src")
        manga.description = element.select(".cComicMemo").text().trim()

        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title").trim()
            manga.thumbnail_url = it.select("img").attr("src").trim()
        }
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select("a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.attr("title").trim()
        }

        return chapter
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
//        manga.description = document.select("p.comic_deCon_d").text().trim()
//        manga.thumbnail_url = document.select("div.comic_i_img > img").attr("src")
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request().url().url().toString()

        val re = Regex(""".*\/(.*?)\/\d+\.html\?s=(\d+)""")

        val matches = re.find(url)?.groups
        val pathId = matches!!.get(1)!!.value
        val pathS = matches!!.get(2)!!.value

        return pageListParse(response.asJsoup(), pathId, pathS)
    }

    override fun pageListParse(document: Document):List<Page> = listOf()

    fun pageListParse(document: Document, id: String, s: String): List<Page> {
        return document.select("#iPageHtm > a").mapIndexed { i, _ ->
            Page(i, String.format("http://www.hhimm.com/%s/%d.html?s=%s&d=0", id, i + 1, s), "")
        }
    }

    override fun imageUrlParse(document: Document): String {
        // get img key
        val imgEleIds = arrayOf("img1021", "img2391", "img7652", "imgCurr")
        var imgKey: String? = null
        for (i in imgEleIds.indices) {
            imgKey = document.select("#" + imgEleIds[i]).attr("name")
            if (imgKey != "") break
        }

        val servers = document.select("#hdDomain").attr("value").split("|")

        //img key decode
        return if (imgKey != "") {
            servers[0] + unsuan(imgKey!!)
        } else ""
    }

    //https://stackoverflow.com/questions/2946067/what-is-the-java-equivalent-to-javascripts-string-fromcharcode
    fun fromCharCode(vararg codePoints: Int): String {
        return String(codePoints, 0, codePoints.size)
    }

    private fun unsuan(s: String): String {
        var s = s
        val sw = "44123.com|hhcool.com|hhimm.com"
        val su = "www.hhimm.com"
        var b = false

        for (i in 0 until sw.split("|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size) {
            if (su.indexOf(sw.split("|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[i]) > -1) {
                b = true
                break
            }
        }
        if (!b)
            return ""

        val x = s.substring(s.length - 1)
        val w = "abcdefghijklmnopqrstuvwxyz"
        val xi = w.indexOf(x) + 1
        val sk = s.substring(s.length - xi - 12, s.length - xi - 1)
        s = s.substring(0, s.length - xi - 12)
        val k = sk.substring(0, sk.length - 1)
        val f = sk.substring(sk.length - 1)

        for (i in 0 until k.length) {
            s = s.replace(k.substring(i, i + 1), Integer.toString(i))
        }
        val ss = s.split(f.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        s = ""
        for (i in ss.indices) {
            s += fromCharCode(Integer.parseInt(ss[i]))
        }
        return s
    }


    private class GenreFilter(genres: Array<String>) : Filter.Select<String>("Genre", genres)

    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList())
    )

    private fun getGenreList() = arrayOf(
        "All"
    )


}

