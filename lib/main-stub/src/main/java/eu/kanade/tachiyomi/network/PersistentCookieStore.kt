package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.net.URI

class PersistentCookieStore(context: Context) {

        fun get(url: HttpUrl) : List<Cookie> = throw Exception("Stub!")

        fun get(uri: URI) : List<Cookie> = throw Exception("Stub!")



}