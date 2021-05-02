package eu.kanade.tachiyomi.extension.en.nightcomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers

class NightComic : Madara("Night Comic", "https://www.nightcomic.com", "en") {
    override val formHeaders: Headers = headersBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .add("X-MOD-SBB-CTYPE", "xhr")
        .build()
}
