package eu.kanade.tachiyomi.extension.all.comicake

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ComiCakeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LetItGoScans(),
        WhimSubs()
    )
}

class LetItGoScans : ComiCake("LetItGo Scans", "https://reader.letitgo.scans.today", "en", "/")
class WhimSubs : ComiCake("WhimSubs", "https://whimsubs.xyz", "en")
