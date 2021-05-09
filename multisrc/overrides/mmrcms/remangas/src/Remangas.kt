package eu.kanade.tachiyomi.extension.pt.remangas

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Remangas : MMRCMS("Remangas", "https://remangas.top", "pt-BR") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()
}
