package eu.kanade.tachiyomi.extension.ko.mangashowme

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class ImageUrlHandlerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()

        // only for image Request
        val host = req.url().host()
        val isFileCdn = !host.contains(".filecdn.xyz") || !host.contains("chickencdn.info")
        if (!req.url().toString().endsWith("?quick")) return chain.proceed(req)

        val secondUrl = req.header("SecondUrlToRequest")

        fun get(flag: Int = 0): Request {
            val url = if (isFileCdn) {
                when (flag) {
                    1 -> req.url().toString().replace("img.", "s3.")
                    else -> req.url().toString()
                }
            } else {
                when (flag) {
                    1 -> secondUrl!!
                    2 -> secondUrl!!.replace("img.", "s3.")
                    else -> req.url().toString().substringBefore("?quick")
                }
            }

            return req.newBuilder()!!.url(url)
                    .removeHeader("ImageDecodeRequest")
                    .removeHeader("SecondUrlToRequest")
                    .build()!!
        }

        val res = chain.proceed(get())

        return if (isFileCdn) {
            val length = res.header("content-length")
            if (length == null || length.toInt() < 50000) {
                chain.proceed(get(1)) // s3
            } else res
        } else {
            if (!res.isSuccessful && secondUrl != null) {
                val fallbackRes = chain.proceed(get(1)) // img filecdn
                val fallbackLength = fallbackRes.header("content-length")
                if (fallbackLength == null || fallbackLength.toInt() < 50000) {
                    chain.proceed(get(2)) // s3
                } else fallbackRes
            } else res
        }
    }
}