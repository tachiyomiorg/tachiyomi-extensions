package eu.kanade.tachiyomi.extension.en.toonmany

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class ToonMany : Madara("ToonMany", "https://toonmany.com", "en", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)) {

}
