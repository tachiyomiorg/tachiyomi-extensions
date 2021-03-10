package eu.kanade.tachiyomi.extension.en.eighteenlhplus

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import okhttp3.OkHttpClient

class EighteenLHPlus : FMReader("18LHPlus", "https://18lhplus.com", "en") {
    override val client: OkHttpClient = super.client.newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                chain.proceed(originalRequest).let { response ->
                    if (response.code() == 403 && originalRequest.url().host().contains("mkklcdn")) {
                        response.close()
                        chain.proceed(originalRequest.newBuilder().removeHeader("Referer").addHeader("Referer", "https://manganelo.com").build())
                    } else {
                        response
                    }
                }
            }
            .build()
    override fun popularMangaNextPageSelector() = "div.col-lg-8 div.btn-group:first-of-type"
    override fun getGenreList() = getAdultGenreList()
}
