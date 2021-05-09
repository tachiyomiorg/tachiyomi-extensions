package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RandomScan : Madara(
    "Random Scan",
    "https://randomscan.online",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()
}
