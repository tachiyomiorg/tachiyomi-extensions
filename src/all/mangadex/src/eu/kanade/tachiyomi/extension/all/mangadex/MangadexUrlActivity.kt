package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.lib.urlhandler.UrlHandlerActivity

/**
 * Springboard that accepts https://mangadex.com/{title,manga}/xxx intents and redirects
 * them to the main tachiyomi process. The idea is to not install the intent filter
 * unless you have this extension installed, but still let the main tachiyomi app control
 * things.
 *
 * Main goal was to make it easier to open manga in Tachiyomi in spite of the DDoS blocking
 * the usual search screen from working.
 */
class MangadexUrlActivity : UrlHandlerActivity() {

    override fun getQueryFromPathSegments(pathSegments: List<String>): String {
        val id = pathSegments[1]
        return "${Mangadex.PREFIX_ID_SEARCH}$id"
    }

}
