package eu.kanade.tachiyomi.extension.en.guya

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GuyaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf( GuyaMoe() )
}

class GuyaMoe : Guya()