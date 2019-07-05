package eu.kanade.tachiyomi.extension.en.genkan

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GenkanFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LeviatanScans(),
        PsychoPlay(),
        OneShotScans(),
        KaguyaDex(),
        KomiScans(),
        HunlightScans())
}

class LeviatanScans : Genkan("Leviatan Scans", "https://leviatanscans.com", "en")
class PsychoPlay : Genkan("Psycho Play", "https://psychoplay.co", "en")
class OneShotScans : Genkan("One Shot Scans", "https://oneshotscans.com", "en")
class KaguyaDex : Genkan("KaguyaDex", " https://kaguyadex.com", "en")
class KomiScans : Genkan("Komi Scans", " https://komiscans.com", "en")
class HunlightScans : Genkan("Hunlight Scans", "https://hunlight-scans.info", "en")
