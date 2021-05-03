package eu.kanade.tachiyomi.extension.en.readnoblessemanhwaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup

class ReadNoblesseManhwaOnline : MangaCatalog("Read Noblesse Manhwa Online", "https://ww2.readnoblesse.com", "en") {
    override val sourceList = listOf(
        Pair("Noblesse", "$baseUrl/manga/noblesse/"),
        Pair("Rai’s Adventure", "$baseUrl/manga/noblesse-rais-adventure/"),
        Pair("NOBLESSE S", "$baseUrl/manga/noblesse-s/"),
        Pair("Ability", "$baseUrl/manga/ability/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
