package eu.kanade.tachiyomi.extension.all.multporn

import eu.kanade.tachiyomi.annotations.MultiSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@MultiSource
class MultPornFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        EN(),
        JA()
    )
}

class EN : MultPorn("en")
class JA : MultPorn("ja")
