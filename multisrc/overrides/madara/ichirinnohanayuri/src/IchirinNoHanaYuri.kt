package eu.kanade.tachiyomi.extension.pt.ichirinnohanayuri

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class IchirinNoHanaYuri : Madara(
    "Ichirin No Hana Yuri",
    "https://ichirinnohanayuri.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 2, TimeUnit.SECONDS))
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())

            if (response.code == 403) {
                response.close()
                throw IOException(BLOCKING_MESSAGE)
            }

            response
        }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()

    companion object {
        private const val BLOCKING_MESSAGE = "O site está bloqueando o Tachiyomi. " +
            "Migre para outra fonte caso o problema persistir."
    }
}
