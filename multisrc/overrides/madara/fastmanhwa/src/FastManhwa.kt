package eu.kanade.tachiyomi.extension.en.fastmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class FastManhwa : Madara("FastManhwa", "https://fastmanhwa.com", "en", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)) {

}
