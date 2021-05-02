package eu.kanade.tachiyomi.extension.tr.mangabaz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBaz : Madara("MangaBaz", "https://mangabaz.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")))
