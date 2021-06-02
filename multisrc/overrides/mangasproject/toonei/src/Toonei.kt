package eu.kanade.tachiyomi.extension.pt.toonei

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class Toonei : MangasProject("Toonei", "https://toonei.net", "pt-BR") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(2, 1, TimeUnit.SECONDS))
        .build()

    override fun getReaderToken(document: Document): String? {
        return document.select("script:containsData(window.PAGES_KEY)").firstOrNull()
            ?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\";")
    }
}
