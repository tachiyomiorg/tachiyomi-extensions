package eu.kanade.tachiyomi.extension.all.mangasum

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSumFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaSum(),
        MangaSumRAW(),
    )
}
class MangaSumRAW : WPComics("MangaSum RAW", "https://mangasum.com", "ja", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/raw" + if (page > 1) "?page=$page" else "", headers)
    }
    override fun popularMangaSelector() = "div.items div.item"
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/genres?keyword=$query&page=$page", headers)
    override fun searchMangaSelector() = "div.items div.item div.image a[title*=' - Raw']"
}

class MangaSum : WPComics("MangaSum", "https://mangasum.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US), null) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/genres?keyword=$query&page=$page", headers)
    override fun searchMangaSelector() = "div.items div.item div.image a:not([title*=' - Raw'])"
}
