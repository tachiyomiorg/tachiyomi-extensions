package eu.kanade.tachiyomi.extension.en.pmscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PMScans : Madara("PMScans", "https://www.pmscans.com", "en", SimpleDateFormat("MMM-dd-yy", Locale.US))
