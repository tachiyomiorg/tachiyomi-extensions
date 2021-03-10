package eu.kanade.tachiyomi.extension.en.nazarickscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET

class NazarickScans : Madara("Nazarick Scans", "https://nazarickscans.com", "en") {
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector(): String? = null
}
