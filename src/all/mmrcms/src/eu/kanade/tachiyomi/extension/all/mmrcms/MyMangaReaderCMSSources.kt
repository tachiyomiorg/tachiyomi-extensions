package eu.kanade.tachiyomi.extension.all.mmrcms

import eu.kanade.tachiyomi.source.SourceFactory

class MyMangaReaderCMSSources: SourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    override fun createSources() = SOURCES
}

