package eu.kanade.tachiyomi.extension.id.ngomik

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import okhttp3.Headers

class Ngomik : WPMangaReader("Ngomik", "https://ngomik.net", "id", "/all-komik") {
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)
}
