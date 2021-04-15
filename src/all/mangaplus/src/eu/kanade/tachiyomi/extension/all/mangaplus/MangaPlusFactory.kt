package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaPlusEnglish(),
        MangaPlusSpanish(),
        MangaPlusThai(),
        MangaPlusPortuguese()
    )
}

class MangaPlusEnglish : MangaPlus("en", "eng", Language.ENGLISH)
class MangaPlusSpanish : MangaPlus("es", "esp", Language.SPANISH)
class MangaPlusThai : MangaPlus("th", "eng", Language.THAI)

// The titles have the Portugal flag in the thumbnail, but the text of the translations is Brazilian.
class MangaPlusPortuguese : MangaPlus("pt-BR", "eng", Language.PORTUGUESE_BR)
