package eu.kanade.tachiyomi.extension.en.dankefurslesen

import eu.kanade.tachiyomi.multisrc.guya.Guya

class DankeFursLesen : Guya("Danke fürs Lesen", "https://danke.moe", "en") {
    companion object {
        const val SLUG_PREFIX = "slug:"
        const val PROXY_PREFIX = "proxy:"
        const val NESTED_PROXY_API_PREFIX = "/proxy/api/"
    }
}
