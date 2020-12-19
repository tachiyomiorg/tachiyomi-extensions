package eu.kanade.tachiyomi.extension.en.firstkissmanga

import eu.kanade.tachiyomi.lib.themesources.madara.Madara
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

class FirstKissManga : Madara("1st Kiss", "https://1stkissmanga.com", "en", dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}
