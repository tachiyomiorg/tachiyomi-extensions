package eu.kanade.tachiyomi.extension.all.wpmangareader

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class WPMangaReaderFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        KomikMama(),
        MangaKita(),
        Ngomik(),
        TurkToon(),
    )
}

class TurkToon : WPMangaReader("TurkToon", "https://turktoon.com", "tr", "/manga", SimpleDateFormat("MMM d, yyyy", Locale("tr")))

class KomikMama : WPMangaReader("KomikMama", "https://komikmama.net", "id", "/manga", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")))

class MangaKita : WPMangaReader("MangaKita", "https://mangakita.net", "id")

class Ngomik : WPMangaReader("Ngomik", "https://ngomik.net", "id", "/all-komik")
