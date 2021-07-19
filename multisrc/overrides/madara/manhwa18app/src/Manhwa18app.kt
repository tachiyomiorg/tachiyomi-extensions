package eu.kanade.tachiyomi.extension.en.manhwa18app

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Manhwa18app : Madara("Manhwa18.app", "https://manhwa18.app", "en", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)) {

}
