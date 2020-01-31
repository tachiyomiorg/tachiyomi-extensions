package eu.kanade.tachiyomi.network

import okhttp3.Call
import okhttp3.Response
import rx.Observable

fun Call.asObservable(): Observable<Response> {
    throw Exception("Stub!")
}

fun Call.asObservableSuccess(): Observable<Response> {
    throw Exception("Stub!")
}
