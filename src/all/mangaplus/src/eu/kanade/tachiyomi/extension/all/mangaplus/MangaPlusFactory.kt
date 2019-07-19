package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMangaPlus()
}

class MangaPlusEnglish : MangaPlus("en", "eng", 0)
class MangaPlusSpanish : MangaPlus("es", "esp", 1)

fun getAllMangaPlus(): List<Source> = listOf(
    MangaPlusEnglish(),
    MangaPlusSpanish()
)
