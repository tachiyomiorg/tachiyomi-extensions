package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory


class MangadexFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMangaDexLanguages()
}
