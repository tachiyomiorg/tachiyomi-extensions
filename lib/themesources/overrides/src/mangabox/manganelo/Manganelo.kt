package eu.kanade.tachiyomi.extension.en.manganelo

import eu.kanade.tachiyomi.lib.themesources.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request


class Manganelo : MangaBox("Manganelo", "https://manganelo.com", "en") {
    // Nelo's date format is part of the base class
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/genre-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.content-genres-item"
    override val latestUrlPath = "genre-all/"
    override val simpleQueryPath = "search/story/"
    override fun searchMangaSelector() = "div.search-story-item, div.content-genres-item"
    override fun getAdvancedGenreFilters(): List<AdvGenre> = getGenreFilters()
        .drop(1)
        .map { AdvGenre(it.first, it.second) }
}
