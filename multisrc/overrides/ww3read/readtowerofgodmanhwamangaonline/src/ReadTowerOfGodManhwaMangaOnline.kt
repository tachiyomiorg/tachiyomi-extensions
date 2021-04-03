package eu.kanade.tachiyomi.extension.en.readtowerofgodmanhwamangaonline

import eu.kanade.tachiyomi.multisrc.ww3read.Ww3Read
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup

class ReadTowerOfGodManhwaMangaOnline : Ww3Read("Read Tower of God Manhwa/Manga Online", "https://ww1.readtowerofgod.com", "en") {
    override val sourceList = listOf(
        Pair("Season 1", "$baseUrl/manga/tower-of-god-season-1/"),
        Pair("Season 2", "$baseUrl/manga/tower-of-god-season-2/"),
        Pair("Season 3", "$baseUrl/manga/tower-of-god-season-3/"),
        Pair("RAW", "$baseUrl/manga/tower-of-god-spoilers-raw/"),
        Pair("SIU Blog Postd", "$baseUrl/manga/siu-blog-post-translation/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
