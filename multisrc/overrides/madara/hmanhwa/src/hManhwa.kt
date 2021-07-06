package eu.kanade.tachiyomi.extension.en.hmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class hManhwa : Madara(
    "hManhwa",
    "https://hmanhwa.com",
    "en",
    dateFormat = SimpleDateFormat("dd MMM", Locale.US)
)
