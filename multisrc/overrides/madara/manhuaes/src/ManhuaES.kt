package eu.kanade.tachiyomi.extension.en.manhuaes

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaES : Madara("Manhua ES", "https://manhuaes.com", "en") {
    override val pageListParseSelector = "div.text-left li, div.text-left div.separator"
}
