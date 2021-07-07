package eu.kanade.tachiyomi.extension.en.mangafast

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Mangafast : Madara(
    "Mangafast",
    "https://manga-fast.com",
    "en",
    dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US)
)
