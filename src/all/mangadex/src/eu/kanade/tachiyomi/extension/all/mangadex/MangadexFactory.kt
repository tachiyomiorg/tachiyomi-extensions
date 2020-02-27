package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangadexFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaDexEnglish(),
        MangaDexPolish(),
        MangaDexItalian(),
        MangaDexRussian(),
        MangaDexGerman(),
        MangaDexFrench(),
        MangaDexVietnamese(),
        MangaDexSpanishSpain(),
        MangaDexSpanishLTAM(),
        MangaDexCatalan(),
        MangaDexPortuguesePortugal(),
        MangaDexPortugueseBrazil(),
        MangaDexSwedish(),
        MangaDexTurkish(),
        MangaDexIndonesian(),
        MangaDexHungarian(),
        MangaDexBulgarian(),
        MangaDexFilipino(),
        MangaDexDutch(),
        MangaDexArabic(),
        MangaDexChineseSimp(),
        MangaDexChineseTrad(),
        MangaDexThai(),
        MangaDexBengali(),
        MangaDexBurmese(),
        MangaDexCzech(),
        MangaDexDanish(),
        MangaDexFinnish(),
        MangaDexGreek(),
        MangaDexJapanese(),
        MangaDexKorean(),
        MangaDexLithuanian(),
        MangaDexMalay(),
        MangaDexMongolian(),
        MangaDexPersian(),
        MangaDexRomanian(),
        MangaDexSerboCroatian(),
        MangaDexUkrainian()
    )
}

class MangaDexPolish : Mangadex("pl", "pl")
class MangaDexItalian : Mangadex("it", "it")
class MangaDexRussian : Mangadex("ru", "ru")
class MangaDexGerman : Mangadex("de", "de")
class MangaDexFrench : Mangadex("fr", "fr")
class MangaDexVietnamese : Mangadex("vi", "vn")
class MangaDexSpanishSpain : Mangadex("es", "es")
class MangaDexSpanishLTAM : Mangadex("es-419", "mx")
class MangaDexCatalan : Mangadex("ca", "ct")
class MangaDexPortuguesePortugal : Mangadex("pt", "pt")
class MangaDexPortugueseBrazil : Mangadex("pt-BR", "br")
class MangaDexSwedish : Mangadex("sv", "se")
class MangaDexTurkish : Mangadex("tr", "tr")
class MangaDexIndonesian : Mangadex("id", "id")
class MangaDexHungarian : Mangadex("hu", "hu")
class MangaDexBulgarian : Mangadex("bg", "bg")
class MangaDexFilipino : Mangadex("fil", "ph")
class MangaDexDutch : Mangadex("nl", "nl")
class MangaDexArabic : Mangadex("ar", "sa")
class MangaDexChineseSimp : Mangadex("zh-Hans", "cn")
class MangaDexChineseTrad : Mangadex("zh-Hant", "hk")
class MangaDexThai : Mangadex("th", "th")
class MangaDexBengali : Mangadex("bn", "bd")
class MangaDexBurmese : Mangadex("my", "mm")
class MangaDexCzech : Mangadex("cs", "cz")
class MangaDexDanish : Mangadex("da", "dk")
class MangaDexFinnish : Mangadex("fi", "fi")
class MangaDexGreek : Mangadex("el", "gr")
class MangaDexJapanese : Mangadex("ja", "jp")
class MangaDexKorean : Mangadex("ko", "kr")
class MangaDexLithuanian : Mangadex("lt", "lt")
class MangaDexMalay : Mangadex("ms", "my")
class MangaDexMongolian : Mangadex("mn", "mn")
class MangaDexPersian : Mangadex("fa", "ir")
class MangaDexRomanian : Mangadex("ro", "ro")
class MangaDexSerboCroatian : Mangadex("sh", "rs")
class MangaDexUkrainian : Mangadex("uk", "ua")
