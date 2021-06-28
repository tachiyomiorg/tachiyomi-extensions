package eu.kanade.tachiyomi.extension.en.setsuscans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers

class SetsuScans : Madara("Setsu Scans", "https://setsuscans.com", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().let {
        it.set("User-Agent", "Tachiyomi ${it["User-Agent"]!!}")
    }

    override val client = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1))
        .build()
}
