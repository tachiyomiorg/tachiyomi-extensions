package eu.kanade.tachiyomi.extension.en.readsololevelingmangamanhwaonline

import eu.kanade.tachiyomi.multisrc.ww3read.Ww3Read
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup

class ReadSoloLevelingMangaManhwaOnline : Ww3Read("Read Solo Leveling Manga/Manhwa Online", "https://readsololeveling.org", "en") {
    override val sourceList = listOf(
        Pair("Solo Levelingr", "$baseUrl/manga/solo-leveling/"),
        Pair("Light Novel", "$baseUrl/manga/solo-leveling-novel/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
