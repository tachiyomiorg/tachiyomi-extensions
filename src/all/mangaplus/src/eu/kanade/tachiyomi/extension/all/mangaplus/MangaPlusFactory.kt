package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllMangaPlus()
}

// The ids are hardcoded because the old name wasn't matching the official service name.
class MangaPlusEnglish : MangaPlus("en", "eng", Language.ENGLISH)
class MangaPlusSpanish : MangaPlus("es", "esp", Language.SPANISH)

fun getAllMangaPlus(): List<Source> = listOf(
    MangaPlusEnglish(),
    MangaPlusSpanish()
)
