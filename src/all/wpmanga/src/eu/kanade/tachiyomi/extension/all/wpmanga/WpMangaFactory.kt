package eu.kanade.tachiyomi.extension.all.wpmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WpMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllWpManga()
}

fun getAllWpManga(): List<Source> {
    return listOf(
            ZeroScans(),
            MangaSushi(),
            LeviatanScans()
    )
}

class ZeroScans : WpManga("Zero Scans", "https://zeroscans.com/", "en")

class MangaSushi : WpManga("Manga Sushi", "https://mangasushi.net/", "en", true, false){
    override fun popularMangaNextPageSelector() = ".load-title"
}

class LeviatanScans : WpManga("Leviatan Scans", "https://leviatanscans.com/", "es")

//Dead Sites
//class TrashScanlations : WpManga("Trash Scanlations", "https://trashscanlations.com/", "en")