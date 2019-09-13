package eu.kanade.tachiyomi.extension.ko.jmana

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

/**
 * JMana Source
 **/
class JMana : ParsedHttpSource() {
    override val name = "JMana"
    override val baseUrl = "https://mangahide.com"
    override val lang: String = "ko"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.conts > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.select("a")
        val titleElement = element.select(".titBox > span").first()
        val link = linkElement.attr("href")
                .replace(" ", "%20")
                .replace(Regex("/[0-9]+(?!.*?/)"), "")

        val manga = SManga.create()
        manga.setUrlWithoutDomain(link)
        manga.title = titleElement.text()
        manga.thumbnail_url = baseUrl + element.select(".imgBox img").attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.page > ul > li"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic_main_frame?tag=null&keyword=null&chosung=null&page=${page - 1}")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        // Can not detect what page is last page but max mangas are 40.
        val hasNextPage = mangas.size == 40

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/?tag=null&keyword=$query&chosung=null&page=${page - 1}")


    override fun mangaDetailsParse(document: Document): SManga {
        val descriptionElement = document.select(".media > .row > .media-body.col-9 > div")
        val thumbnailUrl = document.select(".media > .row > .media-body.col-3 img.media-object-list").attr("src")

        val manga = SManga.create()
        descriptionElement
                .map { it.text() }
                .forEach { text ->
                    when {
                        DETAIL_TITLE in text -> manga.title = text.substringAfter(DETAIL_TITLE).trim()
                        DETAIL_AUTHOR in text -> manga.author = text.substringAfter(DETAIL_AUTHOR).trim()
                        DETAIL_GENRE in text -> manga.genre = text.substringAfter("장르 : [").substringBefore("]").trim()
                        DETAIL_DESCRIPTION in text -> text.substringAfter(DETAIL_DESCRIPTION).trim()
                    }
                }
        manga.thumbnail_url = thumbnailUrl
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun chapterListSelector() = "div.section > .post > .post-content-list"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select(".entry-title a")
        val rawName = linkElement.text()
        val chapterUrl = "${linkElement.attr("href")}?viewstyle=list".replace("book/", "book_frame/")

        val chapter = SChapter.create()
        chapter.url = chapterUrl
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName.trim()
        chapter.date_upload = parseChapterDate(element.select("li.publish-date span").last().text())
        return chapter
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = Regex("([0-9]+)(?:[-.]([0-9]+))?(?:화)")
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull() ?: -1f
        } catch (e: Exception) {
            e.printStackTrace()
            return -1f
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm").parse(date).time
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        try {
            document.select(".view li#view_content2")
                    .map { it.select("div img").attr("src") }
                    .forEach { pages.add(Page(pages.size, "", it)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return pages
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/frame")
    override fun latestUpdatesNextPageSelector() = ""
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = false

        return MangasPage(mangas, hasNextPage)
    }


    //We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList()

    companion object {
        const val DETAIL_TITLE = "제목 : "
        const val DETAIL_GENRE = "장르 : "
        const val DETAIL_AUTHOR = "작가 : "
        const val DETAIL_DESCRIPTION = "설명 : "
    }
}