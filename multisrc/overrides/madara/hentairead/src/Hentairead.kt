package eu.kanade.tachiyomi.extension.en.hentairead

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Hentairead : Madara("HentaiRead", "https://hentairead.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

}
