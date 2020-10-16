package eu.kanade.tachiyomi.extension.zh.manhuagui

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * An OkHttp interceptor modified for Manhuagui extension that handles rate limiting.
 *
 * Examples:
 *
 * permits = 5,  period = 1, unit = seconds  =>  5 requests per second
 * permits = 10, period = 2, unit = minutes  =>  10 requests per 2 minutes
 *
 * @param baseHost {String}   Manhuagui main website URL host, this URL is not protected by CDN, so it has a more aggressive concurrency request limit.
 * @param mainSitePermits {Int}   Number of requests allowed within a period of units to main URL.
 * @param imgCDNPermits {Int}   Number of requests allowed within a period of units to image CDN.
 * @param period {Long}   The limiting duration. Defaults to 1.
 * @param unit {TimeUnit} The unit of time for the period. Defaults to seconds.
 */
class ManhuaguiRateLimitInterceptor(
    private val baseHost: String,
    private val mainSitePermits: Int = 2,
    private val imgCDNPermits: Int = 5,
    private val period: Long = 1,
    private val unit: TimeUnit = TimeUnit.SECONDS
) : Interceptor {

    private val mainSiteRequestQueue = ArrayList<Long>(mainSitePermits)
    private val imgCDNRequestQueue = ArrayList<Long>(imgCDNPermits)
    private val rateLimitMillis = unit.toMillis(period)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url().host() == baseHost) {
            synchronized(mainSiteRequestQueue) {
                val now = SystemClock.elapsedRealtime()
                val waitTime = if (mainSiteRequestQueue.size < mainSitePermits) {
                    0
                } else {
                    val oldestReq = mainSiteRequestQueue[0]
                    val newestReq = mainSiteRequestQueue[mainSitePermits - 1]

                    if (newestReq - oldestReq > rateLimitMillis) {
                        0
                    } else {
                        oldestReq + rateLimitMillis - now // Remaining time
                    }
                }

                if (mainSiteRequestQueue.size == mainSitePermits) {
                    mainSiteRequestQueue.removeAt(0)
                }
                if (waitTime > 0) {
                    mainSiteRequestQueue.add(now + waitTime)
                    Thread.sleep(waitTime) // Sleep inside synchronized to pause queued requests
                } else {
                    mainSiteRequestQueue.add(now)
                }
            }
        } else {
            synchronized(imgCDNRequestQueue) {
                val now = SystemClock.elapsedRealtime()
                val waitTime = if (imgCDNRequestQueue.size < imgCDNPermits) {
                    0
                } else {
                    val oldestReq = imgCDNRequestQueue[0]
                    val newestReq = imgCDNRequestQueue[imgCDNPermits - 1]

                    if (newestReq - oldestReq > rateLimitMillis) {
                        0
                    } else {
                        oldestReq + rateLimitMillis - now // Remaining time
                    }
                }

                if (imgCDNRequestQueue.size == imgCDNPermits) {
                    imgCDNRequestQueue.removeAt(0)
                }
                if (waitTime > 0) {
                    imgCDNRequestQueue.add(now + waitTime)
                    Thread.sleep(waitTime) // Sleep inside synchronized to pause queued requests
                } else {
                    imgCDNRequestQueue.add(now)
                }
            }
        }

        return chain.proceed(chain.request())
    }
}
