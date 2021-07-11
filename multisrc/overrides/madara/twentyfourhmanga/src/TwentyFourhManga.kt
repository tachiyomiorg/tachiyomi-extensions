package eu.kanade.tachiyomi.extension.en.twentyfourhmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class TwentyFourhManga : Madara("24hManga", "https://24hmanga.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

}
