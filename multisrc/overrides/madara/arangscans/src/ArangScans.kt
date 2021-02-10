package eu.kanade.tachiyomi.extension.en.arangscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class ArangScans : Madara("Arang Scans", "https://arangscans.com", "en") {
    override val userAgentRandomizer = ""
    // has very few manga
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga?m_orderby=views", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga?m_orderby=latest", headers)
    override fun latestUpdatesNextPageSelector(): String? = null
}
