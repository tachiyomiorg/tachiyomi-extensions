package eu.kanade.tachiyomi.extension.vi.truyentranhaudioonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Headers
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class TruyenTranhAudioOnline : Madara("TruyenTranhAudio.online", "https://truyentranhaudio.online", "vi", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
    override val formHeaders: Headers = headersBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content img").map { it.attr("abs:src") }
            .filterNot { it.isNullOrEmpty() }
            .distinct()
            .mapIndexed { i, url -> Page(i, "", url) }
    }
}
