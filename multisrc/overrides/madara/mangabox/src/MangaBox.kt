package eu.kanade.tachiyomi.extension.en.mangabox

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBox : Madara(
    "MangaBox",
    "https://mangabox.org",
    "en",
    dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
)
