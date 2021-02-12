package eu.kanade.tachiyomi.extension.id.komikru

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class KomikRu : WPMangaStream("KomikRu", "https://komikru.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")))
