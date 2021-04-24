package eu.kanade.tachiyomi.extension.all.mangazukiclub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangazukiClubFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangazukiClubJP(),
        MangazukiClubKO(),
    )
}
class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club", "ja")
class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club", "ko")
