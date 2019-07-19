package eu.kanade.tachiyomi.extension.all.mangadex

/**
 * Mangadex languages
 */

class MangaDexPolish : Mangadex("pl", "pl", 3)
class MangaDexItalian : Mangadex("it", "it", 6)
class MangaDexRussian : Mangadex("ru", "ru", 7)
class MangaDexGerman : Mangadex("de", "de", 8)
class MangaDexFrench : Mangadex("fr", "fr", 10)
class MangaDexVietnamese : Mangadex("vi", "vn", 12)
class MangaDexSpanishSpain : Mangadex("es", "es", 15)
class MangaDexSpanishLTAM : Mangadex("es-419", "mx", 29)
class MangaDexCatalan : Mangadex("ca", "ct", 33)
class MangaDexPortuguesePortugal : Mangadex("pt", "pt", 17)
class MangaDexPortugueseBrazil : Mangadex("pt-BR", "br", 16)
class MangaDexSwedish : Mangadex("sv", "se", 18)
class MangaDexTurkish : Mangadex("tr", "tr", 26)
class MangaDexIndonesian : Mangadex("id", "id", 27)
class MangaDexHungarian : Mangadex("hu", "hu", 9)
class MangaDexBulgarian : Mangadex("bg", "bg", 14)
class MangaDexFilipino : Mangadex("fil", "ph", 34)
class MangaDexDutch : Mangadex("nl", "nl", 5)
class MangaDexArabic : Mangadex("ar", "sa", 19)
class MangaDexChineseSimp : Mangadex("zh-Hans", "cn", 21)
class MangaDexChineseTrad : Mangadex("zh-Hant", "hk", 35)
class MangaDexThai : Mangadex("th", "th", 32)
class MangaDexBengali : Mangadex("bn", "bd", 22)
class MangaDexBurmese : Mangadex("my", "mm", 37)
class MangaDexCzech : Mangadex("cs", "cz", 24)
class MangaDexDanish : Mangadex("da", "dk", 20)
class MangaDexFinnish : Mangadex("fi", "fi", 11)
class MangaDexGreek : Mangadex("el", "gr", 13)
class MangaDexJapanese : Mangadex("ja", "jp", 2)
class MangaDexKorean : Mangadex("ko", "kr", 28)
class MangaDexLithuanian : Mangadex("lt", "lt", 38)
class MangaDexMalay : Mangadex("ms", "my", 31)
class MangaDexMongolian : Mangadex("mn", "mn", 25)
class MangaDexPersian : Mangadex("fa", "ir", 30)
class MangaDexRomanian : Mangadex("ro", "ro", 23)
class MangaDexSerboCroatian : Mangadex("sh", "rs", 4)
class MangaDexUkrainian : Mangadex("uk", "ua", 36)

fun getAllMangaDexLanguages() = listOf(
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
