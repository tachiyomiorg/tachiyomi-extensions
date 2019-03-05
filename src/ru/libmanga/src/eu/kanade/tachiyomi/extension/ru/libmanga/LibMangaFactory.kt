package eu.kanade.tachiyomi.extension.ru.libmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LibMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllLibManga()
}

fun getAllLibManga(): List<Source> {
    return listOf(
            Mangalib()
    )
}

class Mangalib : LibManga("Mangalib", "https://mangalib.me", "https://img1.mangalib.me")