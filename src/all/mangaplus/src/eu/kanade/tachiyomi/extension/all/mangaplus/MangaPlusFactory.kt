package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.extension.en.mangaplus.MangaPlusEnglish
import eu.kanade.tachiyomi.extension.es.mangaplus.MangaPlusSpanish
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMangaPlus()
}

fun getAllMangaPlus(): List<Source> = listOf(
    MangaPlusEnglish(),
    MangaPlusSpanish()
)
