package eu.kanade.tachiyomi.extension.vi.medoctruyentranh

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class MeDocTruyenTranh : ParsedHttpSource() {

    override val name = "MeDocTruyenTranh"

    override val baseUrl = "http://www.medoctruyentranh.net"

    override val lang = "vi"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = ".morelistCon a"


    override fun popularMangaRequest(page: Int): Request {
        Log.d("popularMangaRequest", "$baseUrl/more/${page + 1}")
        return GET("$baseUrl/more/${page + 1}", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("${baseUrl}href"))
        Log.d("popularManga url", manga.url)
        manga.title = element.attr("title").trim()
        Log.d("popularManga title", manga.title)
        manga.thumbnail_url = element.select("img").first()?.attr("src")
        Log.d("popularManga thum", manga.thumbnail_url)
        return manga
    }

    override fun popularMangaNextPageSelector() = ""

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/$query", headers)
    }

    override fun searchMangaSelector() = ".listCon a"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("div.storytitle").text()
        manga.thumbnail_url = element.select("img").first()?.attr("src")
        return manga
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.detail_infos").first()

        val manga = SManga.create()
        manga.author = infoElement.select("font").first()?.text()
        manga.genre = infoElement.select("div.detail_infos > div:nth-child(3) > div:nth-child(2) font").joinToString { it.text() }
        manga.description = infoElement.select(".summary").text()
        manga.status = infoElement.select("div:nth-child(3) > div:nth-child(1) > font").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = infoElement.select("div.detail_info > img").attr("src")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã kết thúc") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

//    override fun chapterListRequest(manga: SManga): Request {
//        val newHeader = headers.newBuilder().add("{\"Origin\"=\"http://www.medoctruyentranh.net\"; \"Accept-Encoding\"=\"gzip, deflate\"; \"Accept-Lang\n" +
//                "uage\"=\"vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7,ja-JP;q=0.6,ja;q=0.5,fr-FR;q=0.4,fr;q=0.3,zh-CN;q=0.2,zh;q=0.1\"; \"Us\n" +
//                "er-Agent\"=\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.13\n" +
//                "1 Safari/537.36\"; \"Accept\"=\"application/json\"; \"Referer\"=\"http://www.medoctruyentranh.net/readingPage/68184/1\";").build()!!
//        val json = "{`\"story_id`\":68184,`\"chapter_num`\":100,`\"newest_chapter_time`\":1526929975,`\"common_param`\":{`\"clien\n" +
//                "t`\":`\"website_1.0.0`\",`\"version`\":`\"1.0.0`\"}}")
//        val body : RequestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
//                json)
//        return POST("$baseUrl/v1/story/comic/full_chapters", newHeader, body)
//    }
    override fun chapterListSelector() = "div.chapters  a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()
        chapter.date_upload = parseChapterDate(element.select("div.chapter_title > span:nth-child(3)").text().replace("）", "").split(" ").last())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dates: Calendar = Calendar.getInstance()

        // Format eg 18/11/2017
        val dateDMY = date.split("/")
        dates.set(dateDMY[2].toInt(), dateDMY[1].toInt() - 1, dateDMY[0].toInt())
        dates.timeInMillis

        return dates.timeInMillis
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".content-body loadingBg img").forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

}