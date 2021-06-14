package eu.kanade.tachiyomi.extension.all.mangadex

import android.util.Log
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly

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

    private val mdAtHomeUrlRegex =
        Regex("""^https://[\w\d]+\.[\w\d]+\.mangadex(\b-test\b)?\.network.*${'$'}""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return chain.proceed(chain.request()).let { response ->
            val url = originalRequest.url.toString()
            if (url.contains(mdAtHomeUrlRegex)) {
                val cachedImage = response.header("X-Cache", "") == "HIT"
                val jsonObj = buildJsonObject {
                    put("url", url)
                    put("success", response.isSuccessful)
                    put("bytes", response.peekBody(Long.MAX_VALUE).bytes().size)
                    put("duration", response.receivedResponseAtMillis - response.sentRequestAtMillis)
                    put("cached", cachedImage)
                }

                val postResult = client.newCall(
                    POST(
                        MDConstants.atHomePostUrl,
                        headers,
                        jsonObj.toString().toRequestBody(null)
                    )
                )
                try {
                    val body = postResult.execute()
                    body.closeQuietly()
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
