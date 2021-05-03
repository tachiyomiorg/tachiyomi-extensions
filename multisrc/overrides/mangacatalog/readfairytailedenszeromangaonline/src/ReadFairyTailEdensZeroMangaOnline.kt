package eu.kanade.tachiyomi.extension.en.readfairytailedenszeromangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup

class ReadFairyTailEdensZeroMangaOnline : MangaCatalog("Read Fairy Tail & Edens Zero Manga Online", "https://ww4.readfairytail.com", "en") {
    override val sourceList = listOf(
        Pair("Eden's Zero", "$baseUrl/manga/edens-zero/"),
        Pair("Fairy Tail", "$baseUrl/manga/fairy-tail/"),
        Pair("FT Zero", "$baseUrl/manga/fairy-tail-zero/"),
        Pair("FT City Hero", "$baseUrl/manga/fairy-tail-zero/"),
        Pair("Hero’s", "$baseUrl/manga/heros/"),
        Pair("FT Happy Adv", "$baseUrl/manga/fairy-tail-happys-grand-adventure/"),
        Pair("FT 100 Year", "$baseUrl/manga/fairy-tail-100-years-quest/"),
        Pair("FT Ice Trail", "$baseUrl/manga/fairy-tail-ice-trail/"),
        Pair("FT x Taizai", "$baseUrl/manga/fairy-tail-x-nanatsu-no-taizai-christmas-special/"),
        Pair("Parasyte x FT", "$baseUrl/manga/parasyte-x-fairy-tail/"),
        Pair("Monster Hunter", "$baseUrl/manga/monster-hunter-orage/"),
        Pair("Rave Master", "$baseUrl/manga/rave-master/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
