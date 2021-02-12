package eu.kanade.tachiyomi.extension.tr.liebeschneehiver

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class LiebeSchneeHiver : WPMangaStream(
    "Liebe Schnee Hiver",
    "https://www.liebeschneehiver.com",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("tr"))
)
