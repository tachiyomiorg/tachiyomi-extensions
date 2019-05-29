package eu.kanade.tachiyomi.extension.jp.nicoseiga

import eu.kanade.tachiyomi.lib.urlhandler.UrlHandlerActivity

class NicoSeigaUrlActivity : UrlHandlerActivity() {

    override fun getQueryFromPathSegments(pathSegments: List<String>): String {
        val id = pathSegments[1]
        return "${NicoSeiga.PREFIX_ID_SEARCH}$id"
    }

}
