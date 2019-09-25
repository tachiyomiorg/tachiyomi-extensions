package eu.kanade.tachiyomi.extension.all.lhreader

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LHReaderFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LHTranslation(),
        MangaHato(),
        ManhwaScan(),
        MangaTiki(),
        MangaBone()
    )
}

class LHTranslation : LHReader("LHTranslation", "https://lhtranslation.net", "en")
class MangaHato : LHReader("Hato", "https://mangahato.com", "ja")
class ManhwaScan : LHReader("ManhwaScan", "https://manhwascan.com", "en")
class MangaTiki : LHReader("MangaTiki", "https://mangatiki.com", "ja")
class MangaBone : LHReader("MangaBone", "https://mangabone.com", "en")


