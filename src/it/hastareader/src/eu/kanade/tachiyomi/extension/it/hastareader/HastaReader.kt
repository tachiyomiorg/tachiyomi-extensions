package eu.kanade.tachiyomi.extension.it.hastareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.regex.Pattern

class HastaReader : ParsedHttpSource() {
    override val name = "HastaReader"

    override val baseUrl = "https://hastareader.com"

    override val lang = "it"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val pagesUrlPattern by lazy {
            Pattern.compile("""\"url\":\"(.*?)\"""")
        }

        val dateFormat by lazy {
            SimpleDateFormat("dd/MM/yyyy")
        }

        // HastaReader has a check for adult content on some manga.
        private val adultCheck: RequestBody by lazy {
            FormBody.Builder().apply {
                add("adult", "true")
            }.build()
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/slide")

    override fun popularMangaSelector() = "div.list > div.group > div.title > a"

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/slide/directory/$page/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text().trim()
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.next > a.gbutton:contains(Prossimo »)"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/slide/latest/$page/", headers)

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder().apply {
            add("search", query)
        }
        return POST("$baseUrl/slide/search/$page", headers, form.build())
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.panel > div.comic > div.large > div.info").first()

        val manga = SManga.create()
        manga.author = infoElement.select("b:contains(Autore)").first()?.nextSibling()?.toString()?.substringAfter(": ")
        manga.artist = infoElement.select("b:contains(Artista)").first()?.nextSibling()?.toString()?.substringAfter(": ")
        manga.genre = ""
        manga.description = infoElement.select("b:contains(Trama)").first()?.nextSibling()?.toString()?.substringAfter(": ")
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = document.select("div.thumbnail > img")?.attr("src")
        return manga
    }

    override fun mangaDetailsRequest(manga: SManga) = POST(baseUrl + manga.url, headers, adultCheck)

    override fun chapterListSelector() = "div.list > div.group div.element"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("div.title > a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text().trim()
        }
        chapter.date_upload = element.select("div.meta_r").first()?.ownText()?.substringAfterLast(", ")?.trim()?.let {
            parseChapterDate(it)
        } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if (date == "Oggi") {
            Calendar.getInstance().timeInMillis
        } else if (date == "Ieri") {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
        } else {
            try {
                dateFormat.parse(date).time
            } catch (e: ParseException) {
                0L
            }
        }
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers, adultCheck)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body().string()
        val pages = mutableListOf<Page>()

        val p = pagesUrlPattern
        val m = p.matcher(body)

        var i = 0
        while (m.find()) {
            pages.add(Page(i++, "", m.group(1).replace("""\/""", "/")))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    override fun getFilterList() = FilterList()
}