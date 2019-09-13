package eu.kanade.tachiyomi.extension.en.mangareader

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MRPFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            Mangareader(),
            Mangapanda())
}

class Mangareader : MRP("Mangareader", "https://www.mangareader.net")
class Mangapanda : MRP("Mangapanda", "https://www.mangapanda.com")
