package eu.kanade.tachiyomi.extension.en.manhuadex

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaDex : Madara(
    "ManhuaDex",
    "https://manhuadex.com",
    "en",
    dateFormat = SimpleDateFormat("d MMM yyyy ", Locale.US)
) {

}
