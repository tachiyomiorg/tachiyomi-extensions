package eu.kanade.tachiyomi.extension.tr.tempestmanga

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import java.text.SimpleDateFormat
import java.util.Locale

class TempestManga : WPMangaStream("Tempest Manga", "https://manga.tempestfansub.com", "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale("tr"))
)
