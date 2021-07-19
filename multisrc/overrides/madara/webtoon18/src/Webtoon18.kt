package eu.kanade.tachiyomi.extension.en.webtoon18

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Webtoon18 : Madara("Webtoon18", "https://webtoon18.net", "en", dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale.US)) {
    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=latest", headers)
}
