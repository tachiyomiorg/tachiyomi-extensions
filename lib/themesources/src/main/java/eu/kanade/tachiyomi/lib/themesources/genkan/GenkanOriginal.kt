package eu.kanade.tachiyomi.lib.themesources.genkan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

// For sites using the older Genkan CMS that didn't have a search function

open class GenkanOriginal(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : Genkan(name, baseUrl, lang) {

    private var searchQuery = ""
    private var searchPage = 1
    private var nextPageSelectorElement = Elements()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) searchPage = 1
        searchQuery = query
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()
        val document = response.asJsoup()
        searchMatches.addAll(getMatchesFrom(document))

        /* call another function if there's more pages to search
           not doing it this way can lead to a false "no results found"
           if no matches are found on the first page but there are matches
           on subsequent pages */
        nextPageSelectorElement = document.select(searchMangaNextPageSelector())
        while (nextPageSelectorElement.hasText()) {
            searchMatches.addAll(searchMorePages())
        }

        return MangasPage(searchMatches, false)
    }

    // search the given document for matches
    private fun getMatchesFrom(document: Document): MutableList<SManga> {
        val searchMatches = mutableListOf<SManga>()
        document.select(searchMangaSelector())
            .filter { it.text().contains(searchQuery, ignoreCase = true) }
            .map { searchMatches.add(searchMangaFromElement(it)) }

        return searchMatches
    }

    // search additional pages if called
    private fun searchMorePages(): MutableList<SManga> {
        searchPage++
        val nextPage = client.newCall(popularMangaRequest(searchPage)).execute().asJsoup()
        val searchMatches = mutableListOf<SManga>()
        searchMatches.addAll(getMatchesFrom(nextPage))
        nextPageSelectorElement = nextPage.select(searchMangaNextPageSelector())

        return searchMatches
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
}
