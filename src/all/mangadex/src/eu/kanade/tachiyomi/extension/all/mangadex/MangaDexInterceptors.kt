package eu.kanade.tachiyomi.extension.all.mangadex

import android.util.Log
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ImageReportDto
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import uy.kohesive.injekt.injectLazy

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

    private val json: Json by injectLazy()

    private val mdAtHomeUrlRegex =
        Regex("""^https://[\w\d]+\.[\w\d]+\.mangadex(\b-test\b)?\.network.*${'$'}""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return chain.proceed(chain.request()).let { response ->
            val url = originalRequest.url.toString()
            if (url.contains(mdAtHomeUrlRegex)) {
                val byteSize = response.peekBody(Long.MAX_VALUE).bytes().size
                val duration = response.receivedResponseAtMillis - response.sentRequestAtMillis
                val cache = response.header("X-Cache", "") == "HIT"
                val result = ImageReportDto(
                    url,
                    response.isSuccessful,
                    byteSize,
                    cache,
                    duration
                )

                val jsonString = json.encodeToString(result)

                val postResult = client.newCall(
                    POST(
                        MDConstants.atHomePostUrl,
                        headers,
                        jsonString.toRequestBody("application/json".toMediaType())
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
