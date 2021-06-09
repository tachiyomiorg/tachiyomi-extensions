package eu.kanade.tachiyomi.extension.pt.prismascans

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PrismaScans : Madara(
    "Prisma Scans",
    "https://prismascans.net",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override val altName = "Nome alternativo: "

    override fun popularMangaSelector() = "div.page-item-detail.manga"
}
