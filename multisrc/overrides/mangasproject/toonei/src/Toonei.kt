package eu.kanade.tachiyomi.extension.pt.toonei

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import org.jsoup.nodes.Document
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Toonei : MangasProject("Toonei", "https://toonei.com", "pt-BR") {

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(5, 1, TimeUnit.SECONDS))
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun getReaderToken(document: Document): String? {
        return document.select("script:containsData(window.PAGES_KEY)").firstOrNull()
            ?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\";")
    }
}
