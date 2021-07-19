package eu.kanade.tachiyomi.extension.pt.dreamunionscan

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DreamUnionScan : Madara(
    "Dream Union Scan",
    "https://www.dreamunionscan.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::authWarningIntercept)
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override val altName: String = "Nome alternativo: "

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException(NEED_LOGIN)
        }

        return response
    }

    companion object {
        private const val NEED_LOGIN = "É necessário realizar o login via WebView para acessar a fonte."
    }
}
