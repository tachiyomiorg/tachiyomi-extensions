package eu.kanade.tachiyomi.extension.pt.geasshentai

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class GeassHentai : Madara(
    "Geass Hentai",
    "https://geasshentai.xyz",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale.ENGLISH)
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override val altName: String = "Nome alternativo: "

    override fun popularMangaSelector() = "div.page-item-detail.manga"
}
