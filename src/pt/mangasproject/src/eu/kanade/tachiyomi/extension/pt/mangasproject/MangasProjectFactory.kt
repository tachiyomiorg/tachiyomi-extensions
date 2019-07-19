package eu.kanade.tachiyomi.extension.pt.mangasproject

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangasProjectFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllSources()
}

class MangasProjectOriginal : MangasProject("mangásPROJECT", "https://leitor.net")
class MangaLivre : MangasProject("MangaLivre", "https://mangalivre.com")

fun getAllSources(): List<Source> = listOf(
    MangasProjectOriginal(),
    MangaLivre()
)
