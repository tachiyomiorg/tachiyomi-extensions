package eu.kanade.tachiyomi.extension.all.myreadingmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

/**
 *
 */
class MyReadingMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMyReadingMangaLanguages()
}
