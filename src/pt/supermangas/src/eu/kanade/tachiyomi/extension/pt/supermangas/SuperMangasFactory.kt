package eu.kanade.tachiyomi.extension.pt.supermangas

import eu.kanade.tachiyomi.extension.pt.supermangas.source.SuperHentais
import eu.kanade.tachiyomi.extension.pt.supermangas.source.SuperMangas
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class SuperMangasFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        SuperMangas(),
        SuperHentais()
    )
}
