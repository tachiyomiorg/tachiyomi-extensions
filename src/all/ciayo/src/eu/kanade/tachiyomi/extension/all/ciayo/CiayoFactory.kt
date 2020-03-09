package eu.kanade.tachiyomi.extension.all.ciayo

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class CiayoFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        CiayoID(),
        CiayoEN()
    )
}

class CiayoID : Ciayo("id")

class CiayoEN : Ciayo("en")


