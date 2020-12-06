package eu.kanade.tachiyomi.extension.all.wpmangareader

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WPMangaReaderFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        KomikMama(),
        MangaKita(),
        Ngomik()
    )
}

class KomikMama : WPMangaReader("KomikMama", "https://komikmama.net", "id", "/manga-list")

class MangaKita : WPMangaReader("MangaKita", "https://mangakita.net", "id", "/daftar-manga")

class Ngomik : WPMangaReader("Ngomik", "https://ngomik.net", "id", "/all-komik")
