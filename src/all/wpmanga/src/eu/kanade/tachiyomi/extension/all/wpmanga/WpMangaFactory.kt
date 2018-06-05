package eu.kanade.tachiyomi.extension.all.wpmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WpMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllWpManga()
}

fun getAllWpManga(): List<Source> {
    return listOf(
            TrashScanlations(),
            ZeroScans()
    )
}

class TrashScanlations : WpManga("Trash Scanlations", "https://trashscanlations.com/", "en")

class ZeroScans : WpManga("Zero Scans", "https://zeroscans.com/", "en")

