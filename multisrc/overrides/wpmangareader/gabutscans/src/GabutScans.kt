package eu.kanade.tachiyomi.extension.id.gabutscans

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import java.text.SimpleDateFormat
import java.util.Locale

class GabutScans : WPMangaReader(
    "Gabut Scans", "https://gabutscans.com", "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))
) {

    override val hasProjectPage = true
}
