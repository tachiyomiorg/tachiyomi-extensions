package eu.kanade.tachiyomi.extension.en.manhuaes

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaES : Madara("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("dd MMMM, yyyy", Locale("vi"))) {
    override val pageListParseSelector = "div.text-left li"
}
