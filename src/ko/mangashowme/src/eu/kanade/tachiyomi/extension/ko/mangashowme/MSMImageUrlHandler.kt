package eu.kanade.tachiyomi.extension.ko.mangashowme

import okhttp3.Interceptor
import okhttp3.Response

internal class ImageUrlHandlerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = RequestHandler(chain).run()
}

private class RequestHandler(val chain: Interceptor.Chain) {
    val req = chain.request()!!
    val origUrl = req.url().toString()

    fun run(): Response {
        // only for image Request
        if (req.header("ImageRequest") != "1") return chain.proceed(req)

        val secondUrl = req.header("SecondUrlToRequest")

        val res = getRequest(origUrl)
        return if (!isSuccess(res) && secondUrl != null) {
            getRequest(secondUrl)
        } else res
    }

    private fun isSuccess(res: Response): Boolean {
        val length = res.header("content-length")?.toInt() ?: 0
        return !(!res.isSuccessful || length < ManaMoa.MINIMUM_IMAGE_SIZE)
    }

    private fun getRequest(url: String): Response = when {
        ".xyz/" in url -> ownCDNRequestHandler(url)
        else -> outsideRequestHandler(url)
    }

    private fun ownCDNRequestHandler(url: String): Response {
        val res = proceedRequest(url)
        return if (!isSuccess(res)) {
            val s3url = if (url.contains("img.")) {
                url.replace("img.", "s3.")
            } else {
                url.replace("://", "://s3.")
            }
            proceedRequest(s3url) // s3
        } else res
    }

    private fun outsideRequestHandler(url: String): Response {
        val outUrl = url.substringBefore("?quick")
        return proceedRequest(outUrl)
    }

    private fun proceedRequest(url: String): Response = chain.proceed(
            req.newBuilder()!!
                    .url(url)
                    .removeHeader("ImageRequest")
                    .removeHeader("ImageDecodeRequest")
                    .removeHeader("SecondUrlToRequest")
                    .build()!!
    )
}
