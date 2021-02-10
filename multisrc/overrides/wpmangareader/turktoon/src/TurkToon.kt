package eu.kanade.tachiyomi.extension.tr.turktoon

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import java.text.SimpleDateFormat
import java.util.Locale

class TurkToon : WPMangaReader("TurkToon", "https://turktoon.com", "tr",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr")))
