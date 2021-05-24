package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaPlusEnglish(),
        MangaPlusIndonesian(),
        MangaPlusPortuguese(),
        MangaPlusSpanish(),
        MangaPlusThai()
    )
}

class MangaPlusEnglish : MangaPlus("en", "eng", Language.ENGLISH)
class MangaPlusIndonesian : MangaPlus("id", "eng", Language.INDONESIAN)
class MangaPlusPortuguese : MangaPlus("pt-BR", "eng", Language.PORTUGUESE_BR)
class MangaPlusSpanish : MangaPlus("es", "esp", Language.SPANISH)
class MangaPlusThai : MangaPlus("th", "eng", Language.THAI)

