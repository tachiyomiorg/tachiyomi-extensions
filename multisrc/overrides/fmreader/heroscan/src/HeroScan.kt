package eu.kanade.tachiyomi.extension.en.heroscan

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import okhttp3.OkHttpClient

class HeroScan : FMReader("HeroScan", "https://heroscan.com", "en") {
    override val client: OkHttpClient = super.client.newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                chain.proceed(originalRequest).let { response ->
                    if (response.code() == 403 && originalRequest.url().host().contains("b-cdn")) {
                        response.close()
                        chain.proceed(originalRequest.newBuilder().removeHeader("Referer").addHeader("Referer", "https://isekaiscan.com").build())
                    } else {
                        response
                    }
                }
            }
            .build()
}
