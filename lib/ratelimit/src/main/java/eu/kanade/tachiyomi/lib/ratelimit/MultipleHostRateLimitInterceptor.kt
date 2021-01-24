package eu.kanade.tachiyomi.lib.ratelimit

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * An OkHttp interceptor that handles multiple url host's rate limiting separately in one interceptor.
 *
 * Examples: Web page url and image CDN url use different rate limit setting for better user experience.
 *
 * @param rateLimitSettings {Array}   Array of RateLimitSetting.
 */
class MultipleHostRateLimitInterceptor(
        private val rateLimitSettings: Array<RateLimitSetting>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        for (rateLimitSetting in rateLimitSettings) {
            if (chain.request().url().host() != rateLimitSetting.hostName) continue

            synchronized(rateLimitSetting.requestQueue) {
                val now = SystemClock.elapsedRealtime()
                val waitTime = if (rateLimitSetting.requestQueue.size < rateLimitSetting.permits) {
                    0
                } else {
                    val oldestReq = rateLimitSetting.requestQueue[0]
                    val newestReq = rateLimitSetting.requestQueue[rateLimitSetting.permits - 1]

                    if (newestReq - oldestReq > rateLimitSetting.rateLimitMillis) {
                        0
                    } else {
                        oldestReq + rateLimitSetting.rateLimitMillis - now // Remaining time
                    }
                }

                if (rateLimitSetting.requestQueue.size == rateLimitSetting.permits) {
                    rateLimitSetting.requestQueue.removeAt(0)
                }
                if (waitTime > 0) {
                    rateLimitSetting.requestQueue.add(now + waitTime)
                    Thread.sleep(waitTime) // Sleep inside synchronized to pause queued requests
                } else {
                    rateLimitSetting.requestQueue.add(now)
                }
            }
        }
        return chain.proceed(chain.request())
    }
}

/**
 *  RateLimitSetting for MultipleHostRateLimitInterceptor.
 *
 *  Examples:
 *
 *  hostName = search.api.manga.com permits = 5,  period = 1, unit = seconds  =>  5 requests per second to search.api.manga.com
 *  hostName = imagecdn.cloudflare.com permits = 10, period = 2, unit = minutes  =>  10 requests per 2 minutes to imagecdn.cloudflare.com
 *
 *  @param hostName {String} Url host that need rate limiting.
 *  @param permits {Int}     Number of requests allowed within a period of units.
 *  @param period {Long}     The limiting duration. Defaults to 1.
 *  @param unit {TimeUnit}   The unit of time for the period. Defaults to seconds.
 */
data class RateLimitSetting(val hostName: String, val permits: Int, val period: Long = 1, val unit: TimeUnit = TimeUnit.SECONDS) {
    val requestQueue = ArrayList<Long>(permits)
    val rateLimitMillis = unit.toMillis(period)
}
