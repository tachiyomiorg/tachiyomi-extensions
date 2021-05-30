package eu.kanade.tachiyomi.extension.en.freecomiconline

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FreeComicOnline : Madara("Free Comic Online", "https://freecomiconline.me", "en") {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()
}
