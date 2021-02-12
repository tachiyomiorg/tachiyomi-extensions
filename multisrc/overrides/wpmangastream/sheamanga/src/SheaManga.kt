package eu.kanade.tachiyomi.extension.id.sheamanga

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class SheaManga : WPMangaStream(
    "Shea Manga",
    "https://sheamanga.my.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))
)
