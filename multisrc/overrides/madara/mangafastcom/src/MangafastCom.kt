package eu.kanade.tachiyomi.extension.en.mangafastcom

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangafastCom : Madara("Manga-fast.com", "https://manga-fast.com", "en", dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US))
