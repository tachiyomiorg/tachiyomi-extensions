package eu.kanade.tachiyomi.extension.ko.mangashowme

import android.annotation.TargetApi
import android.os.Build
import okhttp3.*
import java.util.*


class DDOSGuardInterceptor : Interceptor {
    @TargetApi(Build.VERSION_CODES.O)
    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code() == 403 && response.body().toString().contains("ddgu.ddos-guard.net")) {
            val url = response.request().url().url()
            val h: String = Base64.getEncoder().encodeToString("${url.protocol}://${url.host}".toByteArray())
            val u: String = Base64.getEncoder().encodeToString("/".toByteArray())

            val formBody: RequestBody = FormBody.Builder()
                .add("u", u)
                .add("h", h)
                .add("p", "")
                .build()

            val postRequest: Request = chain.request().newBuilder()
                .url(url.protocol + "://ddgu.ddos-guard.net/ddgu/")
                .post(formBody)
                .build()

            Thread.sleep(5000)

            chain.proceed(postRequest)
        }

        return response
    }
}
