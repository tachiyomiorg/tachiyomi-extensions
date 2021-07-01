package eu.kanade.tachiyomi.extension.en.24hmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class 24hManga : Madara("24hManga", "https://24hmanga.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

}
