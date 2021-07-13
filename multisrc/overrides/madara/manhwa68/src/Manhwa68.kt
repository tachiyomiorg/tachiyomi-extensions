package eu.kanade.tachiyomi.extension.en.manhwa68

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Manhwa68 : Madara(
    "Manhwa68",
    "https://manhwa68.com",
    "en",
    dateFormat = SimpleDateFormat("MMM d yyyy", Locale.US)
) {

}
