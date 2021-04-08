package eu.kanade.tachiyomi.extension.pt.toonei

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import org.jsoup.nodes.Document
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Toonei : MangasProject("Toonei", "https://toonei.com", "pt-BR") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(5, 1, TimeUnit.SECONDS))
        .build()

    override fun getReaderToken(document: Document): String? {
        return document.select("script:containsData(window.PAGES_KEY)").firstOrNull()
            ?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\";")
    }
}
