package eu.kanade.tachiyomi.extension.en.mangame

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaMe : Madara(
    "MangaMe",
    "https://mangame.org",
    "en",
    dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
) {

}
