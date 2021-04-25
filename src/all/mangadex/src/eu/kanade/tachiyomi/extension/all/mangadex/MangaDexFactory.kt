package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class MangaDexFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaDexEnglish(),
        MangaDexJapanese(),
        MangaDexPolish(),
        MangaDexSerboCroatian(),
        MangaDexDutch(),
        MangaDexItalian(),
        MangaDexRussian(),
        MangaDexGerman(),
        MangaDexHungarian(),
        MangaDexFrench(),
        MangaDexFinnish(),
        MangaDexVietnamese(),
        MangaDexGreek(),
        MangaDexBulgarian(),
        MangaDexSpanishSpain(),
        MangaDexPortugueseBrazil(),
        MangaDexPortuguesePortugal(),
        MangaDexSwedish(),
        MangaDexArabic(),
        MangaDexDanish(),
        MangaDexChineseSimp(),
        MangaDexBengali(),
        MangaDexRomanian(),
        MangaDexCzech(),
        MangaDexMongolian(),
        MangaDexTurkish(),
        MangaDexIndonesian(),
        MangaDexKorean(),
        MangaDexSpanishLTAM(),
        MangaDexPersian(),
        MangaDexMalay(),
        MangaDexThai(),
        MangaDexCatalan(),
        MangaDexFilipino(),
        MangaDexChineseTrad(),
        MangaDexUkrainian(),
        MangaDexBurmese(),
        MangaDexLithuanian(),
        MangaDexHebrew(),
        MangaDexHindi(),
        MangaDexNorwegian(),
        MangaDexOther()
    )
}

/*class MangaDexEnglish : MangaDex("en")
class MangaDexJapanese : MangaDex("ja")
class MangaDexPolish : MangaDex("pl")
class MangaDexSerboCroatian : MangaDex("sh")
class MangaDexDutch : MangaDex("nl")
class MangaDexItalian : MangaDex("it")
class MangaDexRussian : MangaDex("ru")
class MangaDexGerman : MangaDex("de")
class MangaDexHungarian : MangaDex("hu")
class MangaDexFrench : MangaDex("fr")
class MangaDexFinnish : MangaDex("fi")
class MangaDexVietnamese : MangaDex("vi")
class MangaDexGreek : MangaDex("el")
class MangaDexBulgarian : MangaDex("bg")
class MangaDexSpanishSpain : MangaDex("es")
class MangaDexPortugueseBrazil : MangaDex("pt-BR")
class MangaDexPortuguesePortugal : MangaDex("pt")
class MangaDexSwedish : MangaDex("sv")
class MangaDexArabic : MangaDex("ar")
class MangaDexDanish : MangaDex("da")
class MangaDexChineseSimp : MangaDex("zh-Hans")
class MangaDexBengali : MangaDex("bn")
class MangaDexRomanian : MangaDex("ro")
class MangaDexCzech : MangaDex("cs")
class MangaDexMongolian : MangaDex("mn")
class MangaDexTurkish : MangaDex("tr")
class MangaDexIndonesian : MangaDex("id")
class MangaDexKorean : MangaDex("ko")
class MangaDexSpanishLTAM : MangaDex("es-419")
class MangaDexPersian : MangaDex("fa")
class MangaDexMalay : MangaDex("ms")
class MangaDexThai : MangaDex("th")
class MangaDexCatalan : MangaDex("ca")
class MangaDexFilipino : MangaDex("fil")
class MangaDexChineseTrad : MangaDex("zh-Hant")
class MangaDexUkrainian : MangaDex("uk")
class MangaDexBurmese : MangaDex("my")
class MangaDexLithuanian : MangaDex("lt")
class MangaDexHebrew : MangaDex("he")
class MangaDexHindi : MangaDex("hi")
class MangaDexNorwegian : MangaDex("no")
class MangaDexOther : MangaDex("other")*/
