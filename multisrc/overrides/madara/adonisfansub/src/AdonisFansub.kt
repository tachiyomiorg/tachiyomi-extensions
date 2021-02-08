package eu.kanade.tachiyomi.extension.tr.adonisfansub

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

@Nsfw
class AdonisFansub : Madara("Adonis Fansub", "https://manga.adonisfansub.com", "tr") {
    override val userAgentRandomizer = ""
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}
