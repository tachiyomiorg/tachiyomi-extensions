package eu.kanade.tachiyomi.extension.en.leviatanscans

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LeviatanScansFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            LeviatanScans("Leviatan Scans", "https://leviatanscans.com", "en")
    )
}
