package eu.kanade.tachiyomi.extension.id.gurukomik

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class GURUKomik : WPMangaStream("GURU Komik", "https://gurukomik.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")))
