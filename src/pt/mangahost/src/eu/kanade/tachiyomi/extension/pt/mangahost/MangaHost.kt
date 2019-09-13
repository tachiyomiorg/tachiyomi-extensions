package eu.kanade.tachiyomi.extension.pt.mangahost

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaHost : ParsedHttpSource() {

    override val name = "Manga Host"

    override val baseUrl = "https://mangahosted.com"

    override val lang = "pt"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)

    private fun mangaFromElement(element: Element, lazy: Boolean = true): SManga = SManga.create().apply {
        title = element.attr("title").replace(LANG_REGEX.toRegex(), "")
        thumbnail_url = element.select("img.manga").attr(if (lazy) "data-path" else "src")
                .replace(IMAGE_REGEX.toRegex(), "_large.")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/mangas/mais-visualizados$pageStr", headers)
    }

    override fun popularMangaSelector(): String = "div.thumbnail div a.pull-left"

    override fun popularMangaFromElement(element: Element): SManga = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi:has(a.nextpostslink)"

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page != 1) "/page/$page" else ""
        return GET("$baseUrl/lancamentos$pageStr", headers)
    }

    override fun latestUpdatesSelector() = "table.table-lancamentos > tbody > tr > td:eq(0) > a"

    override fun latestUpdatesFromElement(element: Element): SManga = mangaFromElement(element, false)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/find/")!!.newBuilder()
            .addQueryParameter("this", query)

       return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "table.table-search > tbody > tr > td:eq(0) > a"

    override fun searchMangaFromElement(element: Element): SManga = mangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#page > section > div > div.pull-left")

        return SManga.create().apply {
            author = removeLabel(infoElement.select("li:contains(Autor:)").text())
            artist = removeLabel(infoElement.select("li:contains(Desenho (Art):)").text())
            genre = removeLabel(infoElement.select("li:contains(Categoria(s):)").text())
            description = infoElement.select("article").first()?.text()
                ?.substringBefore("Relacionados:")
            status = parseStatus(infoElement.select("li:contains(Status:)").text().orEmpty())
            thumbnail_url = document.select("div#page > section > div > img.thumbnail")
                .attr("src")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ativo") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector(): String
            = "ul.list_chapters li a," +
              "table.table-hover:not(.table-mangas) > tbody > tr"

    override fun chapterFromElement(element: Element): SChapter {
        val isNewLayout = element.tagName() == "a"

        if (isNewLayout) {
            val content = Jsoup.parse(element.attr("data-content"))
            val date = content.select("small.clearfix").text().substringAfter("Adicionado em ")

            return SChapter.create().apply {
                name = element.attr("data-original-title").replace(LANG_REGEX.toRegex(), "")
                scanlator = content.select("small.clearfix strong").text()
                date_upload = parseChapterDate(date, DATE_FORMAT_NEW)
                chapter_number = element.text().toFloatOrNull() ?: 1f
                setUrlWithoutDomain(content.select("div.clearfix a").attr("href"))
            }
        }

        val firstColumn = element.select("td:eq(0)")
        val secondColumn = element.select("td:eq(1)")
        val thirdColumn = element.select("td:eq(2)")

        return SChapter.create().apply {
            name = firstColumn.select("a").text().replace(LANG_REGEX.toRegex(), "")
            scanlator = secondColumn.text()
            date_upload = parseChapterDate(thirdColumn.text(), DATE_FORMAT_OLD)
            setUrlWithoutDomain(firstColumn.select("a").attr("href"))
        }
    }

    private fun parseChapterDate(date: String, format: String) : Long {
        return try {
            SimpleDateFormat(format, Locale.ENGLISH).parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Just to prevent the detection of the crawler.
        val newHeader = headersBuilder()
                .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
                .build()

        return GET(baseUrl + chapter.url, newHeader)
    }

    override fun pageListParse(document: Document): List<Page> {
        val documentStr = document.toString()
        val images = documentStr.substringAfter(SCRIPT_BEGIN).substringBefore(SCRIPT_END)
            .replace(SCRIPT_REGEX.toRegex(), "")

        val newDocument = Jsoup.parse(images)
        val referer = document.select("link[rel='canonical']").first()

        return newDocument.select("a img")
            .mapIndexed { i, el -> Page(i, referer.attr("href"), el.attr("src")) }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun removeLabel(text: String?): String = text!!.substringAfter(":")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"

        private const val LANG_REGEX = "( )?\\((PT-)?BR\\)"
        private const val IMAGE_REGEX = "_(small|medium)\\."

        private const val DATE_FORMAT_OLD = "dd/MM/yyyy"
        private const val DATE_FORMAT_NEW = "MMM d, yyyy"

        private const val SCRIPT_BEGIN = "var images = ["
        private const val SCRIPT_END = "];"
        private const val SCRIPT_REGEX = "[\",]"
    }
}
