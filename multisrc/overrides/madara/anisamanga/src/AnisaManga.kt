package eu.kanade.tachiyomi.extension.tr.anisamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers

class AnisaManga : Madara("Anisa Manga", "https://anisamanga.com", "tr") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "https://anisamanga.com")
}
