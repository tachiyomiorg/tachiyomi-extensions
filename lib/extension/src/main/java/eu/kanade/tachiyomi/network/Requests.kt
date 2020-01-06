package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody

private val DEFAULT_CACHE_CONTROL: CacheControl = throw Exception("Stub!")
private val DEFAULT_HEADERS: Headers = throw Exception("Stub!")
private val DEFAULT_BODY: RequestBody = throw Exception("Stub!")

fun GET(url: String,
        headers: Headers = DEFAULT_HEADERS,
        cache: CacheControl = DEFAULT_CACHE_CONTROL): Request {

    throw Exception("Stub!")
}

fun POST(url: String,
         headers: Headers = DEFAULT_HEADERS,
         body: RequestBody = DEFAULT_BODY,
         cache: CacheControl = DEFAULT_CACHE_CONTROL): Request {

    throw Exception("Stub!")
}
