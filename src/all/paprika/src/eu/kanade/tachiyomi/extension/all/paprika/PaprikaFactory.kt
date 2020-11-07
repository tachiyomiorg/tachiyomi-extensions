package eu.kanade.tachiyomi.extension.all.paprika

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class PaprikaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangazukiXyz(),
        MangaTensei(),
        MangaNelo(),
        MangaWindowClub(),
        MangaDogs(),
        ReadMangaFox(),
        MangaStreamXyz()
    )
}

class MangaStreamXyz : Paprika("MangaStream.xyz", "http://mangastream.xyz", "en")
class ReadMangaFox : Paprika("ReadMangaFox", "http://readmangafox.xyz", "en")
class MangazukiXyz : Paprika("MangaZuki.xyz", "http://mangazuki.xyz", "en")
class MangaTensei : Paprika("MangaTensei", "https://www.mangatensei.com", "en")
class MangaNelo : Paprika("MangaNelos.com", "http://manganelos.com", "en")
class MangaWindowClub : PaprikaAlt("MangaWindow.club", "https://mangawindow.club", "en")
class MangaDogs : Paprika("MangaDogs.fun", "http://mangadogs.fun", "en")
