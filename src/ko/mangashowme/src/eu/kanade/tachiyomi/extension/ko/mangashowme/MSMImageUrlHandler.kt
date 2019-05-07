package eu.kanade.tachiyomi.extension.ko.mangashowme

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class ImageUrlHandlerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()

        // only for image Request
        val httpUrl = req.url()
        if (!httpUrl.toString().endsWith("?quick")) return chain.proceed(req)

        val secondUrl = req.header("SecondUrlToRequest")

        fun get(url: String): Response = when {
            "filecdn.xyz" in url || "chickencdn.info" in url
            -> ownCDNRequestHandler(chain, req, url)
            else -> outsideRequestHandler(chain, req, url)
            }

        val res = get(httpUrl.toString())
        val length = res.header("content-length")
        return if ((!res.isSuccessful || length == null || length.toInt() < 50000) && secondUrl != null) {
            get(secondUrl)
        } else res
    }

    private fun ownCDNRequestHandler(chain: Interceptor.Chain, req: Request, url: String): Response {
        val res = chain.proceed(beforeRequest(req, url))
        val length = res.header("content-length")

        return if (!res.isSuccessful || length == null || length.toInt() < 50000) {
            chain.proceed(beforeRequest(req, url.replace("img.", "s3."))) // s3
        } else res
    }

    private fun outsideRequestHandler(chain: Interceptor.Chain, req: Request, url: String): Response {
        val outUrl = url.substringBefore("?quick")
        return chain.proceed(beforeRequest(req, outUrl))
    }

    private fun beforeRequest(req: Request, url: String): Request {
        return req.newBuilder()!!.url(url)
                .removeHeader("ImageDecodeRequest")
                .removeHeader("SecondUrlToRequest")
                .build()!!
    }
}