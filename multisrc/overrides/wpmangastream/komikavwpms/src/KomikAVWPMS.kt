package eu.kanade.tachiyomi.extension.id.komikavwpms

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class KomikAVWPMS : WPMangaStream(
    "Komik AV (WP Manga Stream)",
    "https://komikav.com",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))
) {
    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
}
