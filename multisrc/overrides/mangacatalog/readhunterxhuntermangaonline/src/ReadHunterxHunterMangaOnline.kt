package eu.kanade.tachiyomi.extension.en.readhunterxhuntermangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup

class ReadHunterxHunterMangaOnline : MangaCatalog("Read Hunter x Hunter Manga Online", "https://ww2.readhxh.com", "en") {
    override val sourceList = listOf(
        Pair("Hunter x Hunter", "$baseUrl/manga/hunter-x-hunter/"),
        Pair("Colored", "$baseUrl/manga/hunter-x-hunter-colored/"),
        Pair("Level E", "$baseUrl/manga/level-e/"),
        Pair("Yu Yu Hakusho", "$baseUrl/manga/yu-yu-hakusho/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
