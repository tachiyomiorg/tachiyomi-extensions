package eu.kanade.tachiyomi.extension.id.komikmama

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import java.text.SimpleDateFormat
import java.util.Locale

class KomikMama : WPMangaReader("KomikMama", "https://komikmama.net", "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")))
