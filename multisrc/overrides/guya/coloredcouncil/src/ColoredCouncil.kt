package eu.kanade.tachiyomi.extension.en.coloredcouncil

import eu.kanade.tachiyomi.multisrc.guya.Guya

class ColoredCouncil : Guya("Colored Council", "https://coloredcouncil.moe", "en") {
    companion object {
        const val SLUG_PREFIX = "slug:"
        const val PROXY_PREFIX = "proxy:"
        const val NESTED_PROXY_API_PREFIX = "/proxy/api/"
    }
}
