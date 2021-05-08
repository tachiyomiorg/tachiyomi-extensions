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
    )
}

class MangaDexEnglish : MangaDex("en", "en")
class MangaDexJapanese : MangaDex("ja", "ja")
class MangaDexPolish : MangaDex("pl", "pl")
class MangaDexSerboCroatian : MangaDex("sh", "sh")
class MangaDexDutch : MangaDex("nl", "nl")
class MangaDexItalian : MangaDex("it", "it")
class MangaDexRussian : MangaDex("ru", "ru")
class MangaDexGerman : MangaDex("de", "de")
class MangaDexHungarian : MangaDex("hu", "hu")
class MangaDexFrench : MangaDex("fr", "fr")
class MangaDexFinnish : MangaDex("fi", "fi")
class MangaDexVietnamese : MangaDex("vi", "vi")
class MangaDexGreek : MangaDex("el", "el")
class MangaDexBulgarian : MangaDex("bg", "bg")
class MangaDexSpanishSpain : MangaDex("es", "es")
class MangaDexPortugueseBrazil : MangaDex("pt-BR", "pt-br")
class MangaDexPortuguesePortugal : MangaDex("pt", "pt")
class MangaDexSwedish : MangaDex("sv", "sv")
class MangaDexArabic : MangaDex("ar", "ar")
class MangaDexDanish : MangaDex("da", "da")
class MangaDexChineseSimp : MangaDex("zh-Hans", "zh")
class MangaDexBengali : MangaDex("bn", "bn")
class MangaDexRomanian : MangaDex("ro", "ro")
class MangaDexCzech : MangaDex("cs", "cs")
class MangaDexMongolian : MangaDex("mn", "mn")
class MangaDexTurkish : MangaDex("tr", "tr")
class MangaDexIndonesian : MangaDex("id", "id")
class MangaDexKorean : MangaDex("ko", "ko")
class MangaDexSpanishLTAM : MangaDex("es-419", "es-la")
class MangaDexPersian : MangaDex("fa", "fa")
class MangaDexMalay : MangaDex("ms", "ms")
class MangaDexThai : MangaDex("th", "th")
class MangaDexCatalan : MangaDex("ca", "ca")
class MangaDexFilipino : MangaDex("fil", "fi")
class MangaDexChineseTrad : MangaDex("zh-Hant", "zh-hk")
class MangaDexUkrainian : MangaDex("uk", "uk")
class MangaDexBurmese : MangaDex("my", "my")
class MangaDexLithuanian : MangaDex("lt", "lt")
class MangaDexHebrew : MangaDex("he", "he")
class MangaDexHindi : MangaDex("hi", "hi")
class MangaDexNorwegian : MangaDex("no", "no")
class Other : MangaDex("other", "NULL")
