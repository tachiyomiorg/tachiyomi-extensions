package eu.kanade.tachiyomi.extension.pt.dropescan

import eu.kanade.tachiyomi.lib.themesources.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class DropeScan : Madara("Drope Scan", "https://dropescan.com", "pt-BR") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}
