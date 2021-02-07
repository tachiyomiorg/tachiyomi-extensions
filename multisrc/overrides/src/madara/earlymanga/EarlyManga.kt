package eu.kanade.tachiyomi.extension.en.earlymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers

class EarlyManga : Madara("EarlyManga", "https://earlymanga.xyz", "en") {
    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", "$baseUrl/manga/")
    }
}
