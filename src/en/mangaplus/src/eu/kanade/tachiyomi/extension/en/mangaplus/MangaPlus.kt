package eu.kanade.tachiyomi.extension.en.mangaplus

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response

class MangaPlus : HttpSource() {
    override val name = "Manga Plus by Shueisha"
    
    override val baseUrl = "https://jumpg-webapi.tokyo-cdn.com/api"
    
    override val lang = "en"
    
    override val supportsLatest = false
    
    override fun popularMangaRequest(page: Int) = throw Exception(NEED_MIGRATION)
    override fun popularMangaParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun latestUpdatesRequest(page: Int) = throw Exception(NEED_MIGRATION)
    override fun latestUpdatesParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception(NEED_MIGRATION)
    override fun searchMangaParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun mangaDetailsParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun chapterListParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun pageListParse(response: Response) = throw Exception(NEED_MIGRATION)
    override fun imageUrlParse(response: Response) = throw Exception(NEED_MIGRATION)
    
    companion object {
        const val NEED_MIGRATION = "Deprecated: Use 'All MangaPlus' instead.\nSource migration is on 'My Library' -> three dots -> 'Source migration'"
    }
}
