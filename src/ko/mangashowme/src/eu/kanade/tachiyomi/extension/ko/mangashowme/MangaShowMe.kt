package eu.kanade.tachiyomi.extension.ko.mangashowme

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * MangaShow.Me Source
 *
 * PS. There's no Popular section. It's just a list of manga. Also not latest updates.
 *     `manga_list` returns latest 'added' manga. not a chapter updates.
 **/
class MangaShowMe : ParsedHttpSource() {
    override val name = "MangaShow.Me"
    override val baseUrl = "https://manamoa.net"
    override val lang: String = "ko"

    // Latest updates currently returns duplicate manga as it separates manga into chapters
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(ImageDecoderInterceptor())
            .build()!!

    //override fun popularMangaSelector() = "div.basic-post-gallery > div >  div.post-row"
    override fun popularMangaSelector() = "div.manga-list-gallery > div > div.post-row"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.select("a")
        val titleElement = element.select(".manga-subject > a").first()

        val manga = SManga.create()
        manga.url = urlTitleEscape(linkElement.attr("href"))
        manga.title = titleElement.text().trim()
        manga.thumbnail_url = urlFinder(element.select(".img-wrap-back").attr("style"))
        return manga
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li:not(.disabled)"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/bbs/page.php?hid=manga_list" +
            if (page > 1) "&page=${page - 1}" else "")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = try {
            !document.select(popularMangaNextPageSelector()).last().hasClass("active")
        } catch (_: Exception) {
            false
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = searchComplexFilterMangaRequestBuilder(baseUrl, page, query, filters)


    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.left-info").first()
        val thumbnailElement = info.select("div.manga-thumbnail").first()
        val publishTypeText = thumbnailElement.select("a.publish_type").text() ?: ""
        val authorText = thumbnailElement.select("a.author").text() ?: ""
        val mangaLike = info.select("div.recommend > i.fa").first().text() ?: "0"
        val mangaChaptersLike = mangaElementsSum(document.select("div.addedAt i.fa.fa-thumbs-up > span"))
        val mangaComments = mangaElementsSum(document.select("div.addedAt i.fa.fa-comment > span"))
        val genres = mutableListOf<String>()
        document.select("div.left-info > .manga-tags > a.tag").forEach {
            genres.add(it.text())
        }

        val manga = SManga.create()
        manga.title = info.select("div.red").text()
        // They using background-image style tag for cover. extract url from style attribute.
        manga.thumbnail_url = urlFinder(thumbnailElement.attr("style"))
        // Only title and thumbnail are provided now.
        // TODO: Implement description when site supports it.
        manga.description = "\nMangaShow.Me doesn't provide manga description currently.\n" +
                "\n\uD83D\uDCDD: ${if (publishTypeText.trim().isBlank()) "Unknown" else publishTypeText}" +
                "\n\uD83D\uDCAC: $mangaComments" +
                "\n👍: $mangaLike ($mangaChaptersLike)"
        manga.author = authorText
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(publishTypeText)
        return manga
    }

    private fun parseStatus(status: String) = when (status.trim()) {
        "주간", "격주", "월간", "격월/비정기", "단행본" -> SManga.ONGOING
        "단편", "완결" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun mangaElementsSum(element: Elements?): String {
        if (element.isNullOrEmpty()) return "0"
        return try {
            String.format("%,d", element.map {
                it.text().toInt()
            }.sum())
        } catch (_: Exception) {
            "0"
        }
    }

    override fun chapterListSelector() = "div.manga-detail-list > div.chapter-list > .slot"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select("a")
        val rawName = linkElement.select("div.title").last()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(linkElement.attr("href"))
        chapter.chapter_number = parseChapterNumber(rawName.text())
        chapter.name = rawName.ownText().trim()
        chapter.date_upload = parseChapterDate(element.select("div.addedAt").text().split(" ").first())
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

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        val calendar = Calendar.getInstance()

        // MangaShow.Me doesn't provide uploaded year now(18/12/15).
        // If received month is bigger then current month, set last year.
        // TODO: Fix years due to lack of info.
        return try {
            val month = date.trim().split('-').first().toInt()
            val currYear = calendar.get(Calendar.YEAR)
            val year = if (month > calendar.get(Calendar.MONTH) + 1) // Before December now, // and Retrieved month is December == 2018.
                currYear - 1 else currYear
            SimpleDateFormat("yyyy-MM-dd").parse("$year-$date").time
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }


    // They are using full url in every links.
    // There's possibility to using another domain for serve manga(s). Like marumaru.
    //override fun pageListRequest(chapter: SChapter) = GET(chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        try {
            val element = document.select("div.col-md-9.at-col.at-main script")
            val imageUrl = element.html().substringAfter("var img_list = [").substringBefore("];")
            val imageUrls = JSONArray("[$imageUrl]")
            val decoder = ImageDecoder("v1", element.html())

            (0 until imageUrls.length())
                    .map { imageUrls.getString(it) }
                    .forEach { pages.add(Page(pages.size, "", decoder.request(it))) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return pages
    }


    // Latest not supported
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("This method should not be called!")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("This method should not be called!")
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("This method should not be called!")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")


    //We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    private fun urlFinder(style: String): String {
        // val regex = Regex("(https?:)?//[-a-zA-Z0-9@:%._\\\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\\\+.~#?&/=]*)")
        // return regex.find(style)!!.value
        return style.substringAfter("background-image:url(").substringBefore(")")
    }

    // Some title contains `&` and `#` which can cause a error.
    private fun urlTitleEscape(title: String): String {
        val url = title.split("&manga_name=")
        return "${url[0]}&manga_name=" +
                url[1].replace("&", "%26").replace("#", "%23")
    }

    override fun getFilterList() = getFilters()

    companion object {
        internal const val V1_CX = 5
        internal const val V1_CY = 5
    }
}