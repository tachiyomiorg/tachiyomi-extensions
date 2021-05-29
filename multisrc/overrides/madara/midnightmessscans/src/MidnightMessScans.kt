package eu.kanade.tachiyomi.extension.en.midnightmessscans

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient

@Nsfw
class MidnightMessScans : Madara("Midnight Mess Scans", "https://midnightmess.org, "en") {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            chain.proceed(originalRequest).let { response ->
                if (response.code == 403) {
                    response.close()
                    chain.proceed(originalRequest.newBuilder().removeHeader("Referer").addHeader("Referer", "https://midnightmess.org").build())
                } else {
                    response
                }
            }
        }
        .build()
}
