package eu.kanade.tachiyomi.source.online.french

/**
 * @file Japscan.kt
 * @brief Defines class Japscan for french source Japscan
 * @date 2018-09-02
 * @version 1.0
 *
 * There is no research page on this source, so searching just returns all mangas from the source
 * There are also no thumbnails for mangas
 */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Japscan : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Japscan"

    override val baseUrl = "https://www.japscan.cc"

    override val lang = "fr"

    override val supportsLatest = true

    override fun popularMangaSelector() = "ul.increment-list > li"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl", headers)
    }

    override fun latestUpdatesSelector() = "#dernieres_sorties > div.manga"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "#theresnone"

    override fun latestUpdatesNextPageSelector() = "#theresnone"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/mangas/", headers)
    }

    override fun searchMangaSelector() = "div#liste_mangas > div.row"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaNextPageSelector() = "#theresnone"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.content").first()
        val rowElement = infoElement.select("div.table > div.row").first()

        val manga = SManga.create()
        manga.author = rowElement.select("div:eq(0)").first()?.text()
        manga.artist = rowElement.select("div:eq(0)").first()?.text()
        manga.genre = rowElement.select("div:eq(2)").first()?.text()
        manga.description = infoElement.select("div.synopsis").first()?.text()

        manga.status = rowElement.select("div:eq(4)").first()?.text().orEmpty().let { parseStatus(it) }

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#liste_chapitres > ul > li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = 0
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("select#pages").first()?.select("option")?.forEach {
            pages.add(Page(pages.size, "$baseUrl${it.attr("value")}"))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String {
        val url = document.getElementById("image").attr("src")
        return url
    }
}
