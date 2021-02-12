package eu.kanade.tachiyomi.extension.ko.manhwa18netraw

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class Manhwa18NetRaw : FMReader("Manhwa18.net Raw", "https://manhwa18.net", "ko") {
    override val requestPath = "manga-list-genre-raw.html"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val onlyRawsUrl = super.searchMangaRequest(page, query, filters).url().newBuilder().addQueryParameter("genre", "raw").toString()
        return GET(onlyRawsUrl, headers)
    }

    override fun getFilterList() = FilterList(super.getFilterList().filterNot { it == GenreList(getGenreList()) })
}
