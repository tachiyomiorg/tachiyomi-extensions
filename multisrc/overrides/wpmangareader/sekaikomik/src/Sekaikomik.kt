package eu.kanade.tachiyomi.extension.id.sekaikomik

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import java.text.SimpleDateFormat
import java.util.Locale


class Sekaikomik : WPMangaReader("Sekaikomik", "https://www.sekaikomik.com", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")))
