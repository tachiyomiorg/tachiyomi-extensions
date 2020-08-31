package eu.kanade.tachiyomi.extension.all.elimangas

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class EliMangasFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        JapScan(),
        JapanRead()
    )
}

// data from /api/mangas/configuration?isCensored=false
class JapScan : EliMangasProvider("JapScan", 4, 1376, 1375, "fr")
class JapanRead : EliMangasProvider("JapanRead", 5, 20, 22, "fr")
