package eu.kanade.tachiyomi.extension.pt.gekkouscan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GekkouScans : MMRCMS("Gekkou Scans", "https://leitor.gekkouscans.com.br", "pt-BR") {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun chapterListSelector() = "ul.domaintld > li.li"

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .add("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    }
}
