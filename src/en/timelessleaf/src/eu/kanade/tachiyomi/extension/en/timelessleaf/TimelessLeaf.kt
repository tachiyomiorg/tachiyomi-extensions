package eu.kanade.tachiyomi.extension.en.timelessleaf

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.util.Locale
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 *  @author Aria Moradi <aria.moradi007@gmail.com>
 */

class TimelessLeaf : HttpSource() {

    override val name = "TimelessLeaf"

    override val baseUrl = "https://timelessleaf.com"

    override val lang = "en"

    override val supportsLatest: Boolean = false

    val mangasPageUrl = baseUrl + "/manga/"

    // popular manga

    override fun popularMangaRequest(page: Int): Request {
        return GET(mangasPageUrl)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // scraping post links
        val articleLinks = document.select(".site-main article a")

        // scraping menus, ignore the ones that are not manga entries
        val pagesWeDontWant = listOf(
            "dropped",
            "more manga",
            "recent"
        ).joinToString(prefix = "(?i)", separator = "|").toRegex()

        // all mangas are in sub menus, go straight for that to deal with less menu items
        val menuLinks = document.select(".sub-menu a").filterNot { element ->
            element.text().toLowerCase(Locale.ROOT).contains(pagesWeDontWant)
        }

        // combine the two lists
        val combinedLinks = articleLinks.map { el ->
            Pair(el.text(), el.attr("href"))
        }.toMutableList().apply {
            val titleList = this.map { it.first }
            menuLinks.forEach { el ->
                val title = el.text()
                // ignore duplicates
                if (titleList.filter { str -> str.startsWith(title, ignoreCase = true) }.isEmpty())
                    add(Pair(title, el.attr("href")))
            }
        }.sortedBy { pair -> pair.first }

        return MangasPage(combinedLinks.map { p ->
            SManga.create().apply {
                title = p.first
                setUrlWithoutDomain(p.second)
            }
        }, false)
    }

    // manga details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            // prefer srcset for higher res images, if not available use src
            thumbnail_url = document.select(".site-main img").attr("srcset").substringBefore(" ")
            if (thumbnail_url == "")
                thumbnail_url = document.select(".site-main img").attr("abs:src")
        }
    }

    // chapter list

    override fun chapterListParse(response: Response): List<SChapter> {
        // some chapters are not hosted at TimelessLeaf itself, so can't do anything about them -> ignore
        val hostedHere = response.asJsoup().select(".site-main a").filter { el ->
            el.attr("href").startsWith(baseUrl)
        }

        return hostedHere.map { el ->
            SChapter.create().apply {
                setUrlWithoutDomain(el.attr("href"))
                name = el.text()
            }
        }.asReversed()
    }

    // page list

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().select(".site-main article .gallery-item img").mapIndexed { index, el ->
            Page(index, "", el.attr("abs:src"))
        }
    }

    // search manga, implementing a local search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                val allManga = popularMangaParse(response)
                val filtered = allManga.mangas.filter { manga -> manga.title.contains(query, ignoreCase = true) }
                MangasPage(filtered, false)
            }
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used.")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used.")
}
