package eu.kanade.tachiyomi.extension.en.mangaclash

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaClash : Madara(
    "Manga Clash",
    "https://mangaclash.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US)
)
