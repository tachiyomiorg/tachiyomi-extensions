package eu.kanade.tachiyomi.extension.all.mangatoro

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaToroFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaToro(),
        MangaToroRAW(),
    )
}

class MangaToroRAW : WPComics("MangaToro RAW", "https://ja.mangatoro.com", "ja")

class MangaToro : WPComics("MangaToro", "https://mangatoro.com", "en")
