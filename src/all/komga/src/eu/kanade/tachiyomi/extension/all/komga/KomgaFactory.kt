package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class KomgaFactory : SourceFactory {

    override fun createSources(): List<Source> =
        listOf(
            Komga(),
            Komga("2"),
            Komga("3")
        )

}
