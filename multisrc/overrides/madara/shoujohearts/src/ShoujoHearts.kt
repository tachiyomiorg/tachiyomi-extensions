package eu.kanade.tachiyomi.extension.en.shoujohearts

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.CacheControl
import okhttp3.Request

class ShoujoHearts : Madara("ShoujoHearts", "https://shoujohearts.com", "en") {
    override fun popularMangaRequest(page: Int): Request =
        POST("$baseUrl/reader/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, true).build(), CacheControl.FORCE_NETWORK)

    override fun latestUpdatesRequest(page: Int): Request =
        POST("$baseUrl/reader/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, false).build(), CacheControl.FORCE_NETWORK)

    override fun searchPage(page: Int): String = "reader/page/$page/"
}
