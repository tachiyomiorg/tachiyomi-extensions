package eu.kanade.tachiyomi.extension.all.mangadex

import android.util.Log
import com.google.gson.Gson
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.POST
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response

/**
 * Rate limit requests ignore covers though
 */

private val coverRegex = Regex("""/images/.*\.jpg""")
private val baseInterceptor = RateLimitInterceptor(3)

val mdRateLimitInterceptor = Interceptor { chain ->
    return@Interceptor when (chain.request().url.toString().contains(coverRegex)) {
        true -> chain.proceed(chain.request())
        false -> baseInterceptor.intercept(chain)
    }
}

/**
 * Interceptor to post to md@home for MangaDex Stats
 */
class MdAtHomeReportInterceptor(
    private val client: OkHttpClient,
    private val headers: Headers
) : Interceptor {

    private val gson: Gson by lazy { Gson() }
    private val mdAtHomeUrlRegex =
        Regex("""^https://[\w\d]+\.[\w\d]+\.mangadex(\b-test\b)?\.network.*${'$'}""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return chain.proceed(chain.request()).let { response ->
            val url = originalRequest.url.toString()
            if (url.contains(mdAtHomeUrlRegex)) {
                val jsonString = gson.toJson(
                    mapOf(
                        "url" to url,
                        "success" to response.isSuccessful,
                        "bytes" to response.peekBody(Long.MAX_VALUE).bytes().size
                    )
                )

                val postResult = client.newCall(
                    POST(
                        MDConstants.atHomePostUrl,
                        headers,
                        RequestBody.create(null, jsonString)
                    )
                )
                try {
                    postResult.execute()
                } catch (e: Exception) {
                    Log.e("MangaDex", "Error trying to POST report to MD@Home: ${e.message}")
                }
            }

            response
        }
    }
}

val coverInterceptor = Interceptor { chain ->
    val originalRequest = chain.request()
    return@Interceptor chain.proceed(chain.request()).let { response ->
        if (response.code == 404 && originalRequest.url.toString()
            .contains(coverRegex)
        ) {
            response.close()
            chain.proceed(
                originalRequest.newBuilder().url(
                    originalRequest.url.toString().substringBeforeLast(".") + ".thumb.jpg"
                ).build()
            )
        } else {
            response
        }
    }
}
