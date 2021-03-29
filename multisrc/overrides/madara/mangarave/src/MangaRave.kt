package eu.kanade.tachiyomi.extension.en.mangarave

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRave : Madara("MangaRave", "http://www.mangarave.com", "en", SimpleDateFormat("MMM-dd-yy", Locale.US))
