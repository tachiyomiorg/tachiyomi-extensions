package eu.kanade.tachiyomi.extension.all.noisemanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NoiseMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NoiseMangaEnglish(),
        NoiseMangaPortuguese()
    )
}

class NoiseMangaEnglish : NoiseManga("en")
class NoiseMangaPortuguese: NoiseManga("pt")
