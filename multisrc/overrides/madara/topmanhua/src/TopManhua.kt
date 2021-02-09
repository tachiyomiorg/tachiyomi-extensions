package eu.kanade.tachiyomi.extension.en.topmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

class TopManhua : Madara("Top Manhua", "https://topmanhua.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}
