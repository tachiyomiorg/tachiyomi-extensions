package eu.kanade.tachiyomi.extension.all.comicake

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ComiCakeFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllComiCake()
}

fun getAllComiCake(): List<Source> {
    return listOf(
            WhimSubs(),
            ChampionScans()
    )
}

class WhimSubs : ComiCake("WhimSubs", "https://whimsubs.xyz", "en")

class ChampionScans : ComiCake("Champion Scans", "https://reader.championscans.com", "en", "/")
