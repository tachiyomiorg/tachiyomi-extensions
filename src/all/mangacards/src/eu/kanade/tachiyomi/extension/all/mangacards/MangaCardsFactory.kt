package eu.kanade.tachiyomi.extension.all.mangacards

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaCardsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ValhallaScans(),
        NaniScans()
    )
}

class ValhallaScans : MangaCards("Valhalla Scans", "https://valhallascans.com", "en")
class NaniScans : MangaCards("NANI? Scans", "https://naniscans.xyz", "en")

