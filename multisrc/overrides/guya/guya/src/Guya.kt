package eu.kanade.tachiyomi.extension.en.guya

import eu.kanade.tachiyomi.multisrc.guya.Guya

class Guya : Guya("Guya", "https://guya.moe", "en"){
    companion object {
        const val SLUG_PREFIX = "slug:"
        const val PROXY_PREFIX = "proxy:"
        const val NESTED_PROXY_API_PREFIX = "/proxy/api/"
    }
}
