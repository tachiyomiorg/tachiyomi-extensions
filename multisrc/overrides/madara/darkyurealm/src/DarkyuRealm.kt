package eu.kanade.tachiyomi.extension.pt.darkyurealm

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DarkyuRealm : Madara(
    "Darkyu Realm",
    "https://darkyuerealm.site",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()
}
