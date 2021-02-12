package eu.kanade.tachiyomi.extension.en.manhwa18net

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class Manhwa18NetFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            Manhwa18Net(),
            Manhwa18NetRaw(),
    )
}

class Manhwa18Net : FMReader("Manhwa18.net", "https://manhwa18.net", "en") {
    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=views&sort_type=DESC&ungenre=raw", headers)

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=last_update&sort_type=DESC&ungenre=raw", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val noRawsUrl = super.searchMangaRequest(page, query, filters).url().newBuilder().addQueryParameter("ungenre", "raw").toString()
        return GET(noRawsUrl, headers)
    }

    override fun getGenreList() = getAdultGenreList()
}

class Manhwa18NetRaw : FMReader("Manhwa18.net", "https://manhwa18.net", "ko") {
    override val requestPath = "manga-list-genre-raw.html"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val onlyRawsUrl = super.searchMangaRequest(page, query, filters).url().newBuilder().addQueryParameter("genre", "raw").toString()
        return GET(onlyRawsUrl, headers)
    }

    override fun getFilterList() = FilterList(super.getFilterList().filterNot { it == GenreList(getGenreList()) })
}
