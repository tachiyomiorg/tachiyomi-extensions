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

    // popular manga

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        // scraping menus, ignore the ones that are not manga entries
        val pagesWeDontWant = listOf(
            "dropped",
            "more manga",
            "recent"
        ).joinToString(prefix = "(?i)", separator = "|").toRegex()

        // all mangas are in sub menus, go straight for that to deal with less menu items
        val links = response.asJsoup().select(".sub-menu a").filterNot { element ->
            element.text().toLowerCase(Locale.ROOT).contains(pagesWeDontWant)
        }

        return MangasPage(links.map { el ->
            SManga.create().apply {
                title = el.text()
                setUrlWithoutDomain(el.attr("href"))
            }
        }, false)
    }

    // manga details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(manga, response).apply { initialized = true }
            }
    }

    // change signature to recycle existing data
    private fun mangaDetailsParse(manga: SManga, response: Response): SManga {
        val document = response.asJsoup()

        return manga.apply {
            // prefer srcset for higher res images, if not available use src
            thumbnail_url = document.select(".site-main img").attr("srcset").split(" ")[0]
            if (thumbnail_url == "")
                thumbnail_url = document.select(".site-main img").attr("src")
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
        val document = response.asJsoup()

        return document.select(".site-main article .gallery-item img").mapIndexed { index, el ->
            Page(index, "", el.attr("src"))
        }
    }

    // search manga, implementing a local search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                val allManga = popularMangaParse(response)
                val filtered = allManga.mangas.filter { manga -> manga.title.toLowerCase(Locale.ROOT).contains(query) }
                MangasPage(filtered, false)
            }
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used.")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used.")

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used.")
}
