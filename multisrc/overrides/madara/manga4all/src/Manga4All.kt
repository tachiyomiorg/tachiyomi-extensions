package eu.kanade.tachiyomi.extension.en.manga4all

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Manga4All : Madara(
    "Manga4All",
    "https://manga4all.net",
    "en",
    dateFormat = SimpleDateFormat("dd MMMMMM yyyy ", Locale.US)
) {

}
