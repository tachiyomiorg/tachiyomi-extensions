package eu.kanade.tachiyomi.extension.all.genkan

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GenkanFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            LeviatanScans(),
            LeviatanScansES(),
            PsychoPlay(),
            OneShotScans(),
            KaguyaDex(),
            KomiScans(),
            HunlightScans(),
            WoweScans()
    )
}

/* Genkan class is for the latest version of Genkan CMS
   GenkanOriginal is for the initial version of the CMS that didn't have its own search function  */

class LeviatanScans : Genkan("Leviatan Scans", "https://leviatanscans.com", "en")
class LeviatanScansES : GenkanOriginal("Leviatan Scans", "https://es.leviatanscans.com", "es")
class PsychoPlay : Genkan("Psycho Play", "https://psychoplay.co", "en")
class OneShotScans : Genkan("One Shot Scans", "https://oneshotscans.com", "en")
class KaguyaDex : GenkanOriginal("KaguyaDex", " https://kaguyadex.com", "en")
class KomiScans : GenkanOriginal("Komi Scans", " https://komiscans.com", "en")
class HunlightScans : Genkan("Hunlight Scans", "https://hunlight-scans.info", "en")
class WoweScans : Genkan("Wowe Scans", "https://wowescans.co", "en")
