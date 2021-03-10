package eu.kanade.tachiyomi.extension.en.voidscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList

class Voidscans : Madara("Void Scans", "https://voidscans.com", "en") {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/?s=$query&post_type=wp-manga")
}
