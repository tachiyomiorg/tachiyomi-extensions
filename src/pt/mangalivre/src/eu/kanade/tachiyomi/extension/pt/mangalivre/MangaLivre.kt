package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response
import java.lang.Exception

class MangaLivre : HttpSource() {
    override val name = "MangaLivre"

    override val baseUrl = "https://mangalivre.com"

    override val lang = "pt"

    override val supportsLatest = true

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
        private const val NEED_MIGRATION = "Catálogo incorporado na nova versão da extensão mangásPROJECT."
    }
}
