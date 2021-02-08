package eu.kanade.tachiyomi.extension.en.hunterfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request

class HunterFansub : Madara("Hunter Fansub", "https://hunterfansub.com", "es") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca/page/$page?m_orderby=views", headers)
    override fun popularMangaNextPageSelector() = "div.nav-previous"
    override val popularMangaUrlSelector = "div.post-title a:last-child"
}