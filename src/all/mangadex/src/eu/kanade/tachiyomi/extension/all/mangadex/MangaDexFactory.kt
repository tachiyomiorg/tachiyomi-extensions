package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

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
        MangaDexNorwegian()
    )
}
class MangaDexEnglish : MangaDex("en", "gb")
class MangaDexJapanese : MangaDex("ja", "jp")
class MangaDexPolish : MangaDex("pl", "pl")
class MangaDexSerboCroatian : MangaDex("sh", "rs")
class MangaDexDutch : MangaDex("nl", "nl")
class MangaDexItalian : MangaDex("it", "it")
class MangaDexRussian : MangaDex("ru", "ru")
class MangaDexGerman : MangaDex("de", "de")
class MangaDexHungarian : MangaDex("hu", "hu")
class MangaDexFrench : MangaDex("fr", "fr")
class MangaDexFinnish : MangaDex("fi", "fi")
class MangaDexVietnamese : MangaDex("vi", "vn")
class MangaDexGreek : MangaDex("el", "gr")
class MangaDexBulgarian : MangaDex("bg", "bg")
class MangaDexSpanishSpain : MangaDex("es", "es")
class MangaDexPortugueseBrazil : MangaDex("pt-BR", "br")
class MangaDexPortuguesePortugal : MangaDex("pt", "pt")
class MangaDexSwedish : MangaDex("sv", "se")
class MangaDexArabic : MangaDex("ar", "sa")
class MangaDexDanish : MangaDex("da", "dk")
class MangaDexChineseSimp : MangaDex("zh-Hans", "cn")
class MangaDexBengali : MangaDex("bn", "bd")
class MangaDexRomanian : MangaDex("ro", "ro")
class MangaDexCzech : MangaDex("cs", "cz")
class MangaDexMongolian : MangaDex("mn", "mn")
class MangaDexTurkish : MangaDex("tr", "tr")
class MangaDexIndonesian : MangaDex("id", "id")
class MangaDexKorean : MangaDex("ko", "kr")
class MangaDexSpanishLTAM : MangaDex("es-419", "mx")
class MangaDexPersian : MangaDex("fa", "ir")
class MangaDexMalay : MangaDex("ms", "my")
class MangaDexThai : MangaDex("th", "th")
class MangaDexCatalan : MangaDex("ca", "ct")
class MangaDexFilipino : MangaDex("fil", "ph")
class MangaDexChineseTrad : MangaDex("zh-Hant", "hk")
class MangaDexUkrainian : MangaDex("uk", "ua")
class MangaDexBurmese : MangaDex("my", "mm")
class MangaDexLithuanian : MangaDex("lt", "il")
class MangaDexHebrew : MangaDex("he", "il")
class MangaDexHindi : MangaDex("hi", "in")
class MangaDexNorwegian : MangaDex("no", "no")
