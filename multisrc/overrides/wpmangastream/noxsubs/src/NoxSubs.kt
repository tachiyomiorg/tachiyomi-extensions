package eu.kanade.tachiyomi.extension.tr.noxsubs

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class NoxSubs : WPMangaStream("NoxSubs", "https://noxsubs.com", "tr", SimpleDateFormat("MMM d, yyyy", Locale("tr")))
