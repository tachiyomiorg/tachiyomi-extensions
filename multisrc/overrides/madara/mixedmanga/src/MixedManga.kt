package eu.kanade.tachiyomi.extension.en.mixedmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

class MixedManga : Madara("Mixed Manga", "https://mixedmanga.com", "en", SimpleDateFormat("d MMM yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}
