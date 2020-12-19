package eu.kanade.tachiyomi.extension.th.cattranslator

import eu.kanade.tachiyomi.lib.themesources.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.CacheControl
import okhttp3.Request

class CatTranslator : Madara("CAT-translator", "https://cat-translator.com", "th") {
    override fun popularMangaRequest(page: Int): Request =
        POST("$baseUrl/manga/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, true).build(), CacheControl.FORCE_NETWORK)

    override fun latestUpdatesRequest(page: Int): Request =
        POST("$baseUrl/manga/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, false).build(), CacheControl.FORCE_NETWORK)

    override fun searchPage(page: Int): String = "manga/page/$page/"
}
