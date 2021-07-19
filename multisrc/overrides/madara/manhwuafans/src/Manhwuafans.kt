package eu.kanade.tachiyomi.extension.en.manhwuafans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Manhwuafans : Madara("Manhwua.fans", "https://manhwua.fans", "en", dateFormat = SimpleDateFormat("yyyy'年'M'月'd", Locale.US)) {

}
