package eu.kanade.tachiyomi.extension.all.leviatanscans

import eu.kanade.tachiyomi.lib.themesources.genkan.Genkan
import eu.kanade.tachiyomi.lib.themesources.genkan.GenkanOriginal
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LeviatanScansFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Genkan("Leviatan Scans", "https://leviatanscans.com", "en"),
        GenkanOriginal("Leviatan Scans", "https://es.leviatanscans.com", "es"),
    )
}
