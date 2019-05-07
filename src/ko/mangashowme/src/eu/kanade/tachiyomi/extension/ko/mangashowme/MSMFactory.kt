package eu.kanade.tachiyomi.extension.ko.mangashowme

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response


/*
 * Source Factory for before-Migration
 *
 * I will remove this and only use ManaMoa class after 1.2.15.
 * This is just helper who uses =<1.2.11 before.
 *
 */
class MSMFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            ManaMoa(),
            MSMDeprecated()
    )
}

class MSMDeprecated : HttpSource() {
    override val name = "MangaShow.Me"
    override val baseUrl: String = "https://Depricated._Need.Source.Migration.to.ManaMoa.net"
    override val lang: String = "ko"
    override val supportsLatest = false

    override fun chapterListParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun popularMangaRequest(page: Int) = throw Exception(NEED_MIGRATION)
    override fun popularMangaParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception(NEED_MIGRATION)
    override fun searchMangaParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun latestUpdatesRequest(page: Int) = throw Exception(NEED_MIGRATION)
    override fun latestUpdatesParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun mangaDetailsParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun imageUrlParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun pageListParse(response: Response) = throw Exception(NEED_MIGRATION)

    companion object {
        const val NEED_MIGRATION = "Deprecated: Use 'ManaMoa' instead.\nSource migration is on 'My Library' -> three dots -> 'Source migration'"
    }
}