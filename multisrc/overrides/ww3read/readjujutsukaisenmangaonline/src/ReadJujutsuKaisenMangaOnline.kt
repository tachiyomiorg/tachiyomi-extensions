package eu.kanade.tachiyomi.extension.en.readjujutsukaisenmangaonline

import eu.kanade.tachiyomi.multisrc.ww3read.Ww3Read
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup

class ReadJujutsuKaisenMangaOnline : Ww3Read("Read Jujutsu Kaisen Manga Online", "https://ww1.readjujutsukaisen.com", "en") {
    override val sourceList = listOf(
        Pair("Jujutsu Kaisen", "$baseUrl/manga/jujutsu-kaisen/"),
        Pair("Jujutsu Kaisen 0", "$baseUrl/manga/jujutsu-kaisen-0/"),
        Pair("JJK Light Novel", "$baseUrl/manga/jujutsu-kaisen-first-light-novel/"),
        Pair("No.9", "$baseUrl/manga/no-9/"),
        Pair("JJK Colored", "$baseUrl/manga/jujutsu-kaisen-colored/"),
        Pair("Fanbook", "$baseUrl/manga/jujutsu-kaisen-official-fanbook/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
