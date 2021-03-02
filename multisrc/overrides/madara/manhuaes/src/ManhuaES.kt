package eu.kanade.tachiyomi.extension.en.manhuaes

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaES : Madara("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("dd MMMM, yyyy", Locale("vi"))) {
    override val pageListParseSelector = "div.text-left li"
}
