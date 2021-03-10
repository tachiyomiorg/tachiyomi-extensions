package eu.kanade.tachiyomi.extension.en.mangasy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.CacheControl
import okhttp3.Request

class MangaSY : Madara("Manga SY", "https://www.mangasy.com", "en") {
    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()
}
