package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.lib.urlhandler.UrlHandlerActivity

/**
 * Springboard that accepts https://nhentai.net/g/xxxxxx intents and redirects them to
 * the main Tachiyomi process.
 */
class NHUrlActivity : UrlHandlerActivity() {

    override fun getQueryFromPathSegments(pathSegments: List<String>): String {
        val id = pathSegments[1]
        return "${NHentai.PREFIX_ID_SEARCH}$id"
    }

}
