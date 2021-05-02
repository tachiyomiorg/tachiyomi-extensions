package eu.kanade.tachiyomi.extension.bg.utsukushii

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Utsukushii : MMRCMS("Utsukushii", "https://manga.utsukushii-bg.com", "bg"){
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list", headers)
    }
}
