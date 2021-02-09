package eu.kanade.tachiyomi.extension.id.kombatch

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Kombatch : Madara("Kombatch", "https://kombatch.com", "id", SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("id")))
