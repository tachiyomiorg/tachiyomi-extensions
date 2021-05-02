package eu.kanade.tachiyomi.extension.en.wescans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class WeScans : Madara("WeScans", "https://wescans.xyz", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=views", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/manhua/?s=$query&post_type=wp-manga")
    override fun searchMangaNextPageSelector(): String? = null
    override fun getFilterList(): FilterList = FilterList()
}
