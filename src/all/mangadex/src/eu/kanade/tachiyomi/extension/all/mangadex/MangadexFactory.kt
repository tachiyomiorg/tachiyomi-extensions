package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

/**
 * Created by Carlos on 2/8/2018.
 */
class MangadexFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMangaDexLanguages()
}
