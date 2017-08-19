package eu.kanade.tachiyomi.extension.it.hentaifantasy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.regex.Pattern

class HentaiFantasy : ParsedHttpSource() {
    override val name = "HentaiFantasy"

    override val baseUrl = "http://www.hentaifantasy.it/index.php"

    override val lang = "it"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val pagesUrlPattern by lazy {
            Pattern.compile("""\"url\":\"(.*?)\"""")
        }

        val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd")
        }
    }

    override fun popularMangaSelector() = "div.list > div.group > div.title > a"

    override fun popularMangaRequest(page: Int)
        = GET("$baseUrl/most_downloaded/$page/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text().trim()
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.next > a.gbutton:contains(»):last-of-type"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int)
        = GET("$baseUrl/latest/$page/", headers)

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.length < 3) {
            throw Exception("Inserisci almeno tre caratteri")
        }

        val form = FormBody.Builder().apply {
            add("search", query)
        }

        // As of right now, I have not seen hentaifantasy.it return
        // more than one page when searching by keyword.
        return POST("${baseUrl}/search/", headers, form.build())
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        var genres = mutableListOf<String>()
        document.select("div#tablelist > div.row").forEach { row ->
            when (row.select("div.cell > b").first().text().trim()) {
                "Autore" -> manga.author = row.select("div.cell > a").text().trim()
                "Genere", "Tipo" -> row.select("div.cell > a > span.label").forEach {
                    genres.add(it.text().trim())
                }
                "Descrizione" -> manga.description = row.select("div.cell").last().text().trim()
            }
        }
        manga.genre = genres.joinToString(", ")
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = document.select("div.thumbnail > img")?.attr("src")
        return manga
    }

    override fun mangaDetailsRequest(manga: SManga) = POST(baseUrl + manga.url, headers)

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

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body().string()
        val pages = mutableListOf<Page>()

        val p = pagesUrlPattern
        val m = p.matcher(body)

        var i = 0
        while (m.find()) {
            pages.add(Page(i++, "", m.group(1).replace("""\\""", "")))
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