package eu.kanade.tachiyomi.extension.es.fusionscanlation

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Locale

class FusionScanlation : WPMangaReader("Fusion Scanlation", "https://fusionscanlation.com", "es", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))) {
    private val rateLimitInterceptor = RateLimitInterceptor(1,2)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()
}
