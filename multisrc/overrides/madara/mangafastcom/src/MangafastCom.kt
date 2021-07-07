package eu.kanade.tachiyomi.extension.en.mangafastcom

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangafastCom : Madara("Mangafast.com", "https://manga-fast.com", "en", dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US))
