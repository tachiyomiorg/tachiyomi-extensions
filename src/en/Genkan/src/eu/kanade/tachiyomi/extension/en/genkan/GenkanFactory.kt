package eu.kanade.tachiyomi.extension.en.genkan

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GenkanFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LeviatanScans(),
        PsychoPlay(),
        OneShotScans())
}

class LeviatanScans : Genkan("Leviatan Scans", "https://leviatanscans.com", "en")
class PsychoPlay : Genkan("Psycho Play", "https://psychoplay.co", "en")
class OneShotScans : Genkan("One Shot Scans", "https://oneshotscans.com", "en")
