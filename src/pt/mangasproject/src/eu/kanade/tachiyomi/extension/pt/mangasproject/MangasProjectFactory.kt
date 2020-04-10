package eu.kanade.tachiyomi.extension.pt.mangasproject

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangasProjectFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LeitorNet(),
        MangaLivre()
    )
}

class LeitorNet : MangasProject("Leitor.net", "https://leitor.net") {
    // Use the old generated id when the source did have the name "mangásPROJECT" and
    // did have mangas in their catalogue. Now they "only have webtoons" and
    // became a different website, but they still use the same structure.
    // Existing mangas and other titles in the library still work.
    override val id: Long = 2225174659569980836
}

class MangaLivre : MangasProject("MangaLivre", "https://mangalivre.net") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 4762777556012432014
}
