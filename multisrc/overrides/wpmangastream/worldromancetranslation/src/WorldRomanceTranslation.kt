package eu.kanade.tachiyomi.extension.id.worldromancetranslation

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class WorldRomanceTranslation : WPMangaStream("World Romance Translation", "https://wrt.my.id/", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id")))
