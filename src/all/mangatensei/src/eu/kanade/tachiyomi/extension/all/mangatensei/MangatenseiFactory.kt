package eu.kanade.tachiyomi.extension.all.mangatensei

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory


class MangatenseiFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMangatenseiLanguages()
}