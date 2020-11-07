package eu.kanade.tachiyomi.extension.ja.mangaraw

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaRawFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Manga1000(),
        Manga1001()
    )
}

class Manga1000 : MangaRaw("Manga1000", "http://manga1000.com")
class Manga1001 : MangaRaw("Manga1001", "http://manga1001.com")
