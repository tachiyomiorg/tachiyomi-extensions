package eu.kanade.tachiyomi.extension.en.hscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Hscans : Madara("Hscans", "https://hscans.com", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("es")))
