package eu.kanade.tachiyomi.extension.all.mangatensei

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangatenseiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangatenseiArabic(),
        MangatenseiBrazilian(),
        MangatenseiCzech(),
        MangatenseiDanish(),
        MangatenseiDutch(),
        MangatenseiEnglish(),
        MangatenseiFilipino(),
        MangatenseiFrench(),
        MangatenseiGerman(),
        MangatenseiGreek(),
        MangatenseiHebrew(),
        MangatenseiHungarian(),
        MangatenseiIndonesian(),
        MangatenseiItalian(),
        MangatenseiMalay(),
        MangatenseiPolish(),
        MangatenseiPortuguese(),
        MangatenseiRomanian(),
        MangatenseiRussian(),
        MangatenseiSpanish(),
        MangatenseiThai(),
        MangatenseiTurkish(),
        MangatenseiVietnamese(),

        MangawindowArabic(),
        MangawindowBrazilian(),
        MangawindowCzech(),
        MangawindowDanish(),
        MangawindowDutch(),
        MangawindowEnglish(),
        MangawindowFilipino(),
        MangawindowFrench(),
        MangawindowGerman(),
        MangawindowGreek(),
        MangawindowHebrew(),
        MangawindowHungarian(),
        MangawindowIndonesian(),
        MangawindowItalian(),
        MangawindowMalay(),
        MangawindowPolish(),
        MangawindowPortuguese(),
        MangawindowRomanian(),
        MangawindowRussian(),
        MangawindowSpanish(),
        MangawindowThai(),
        MangawindowTurkish(),
        MangawindowVietnamese(),

        BatotoArabic(),
        BatotoBrazilian(),
        BatotoCzech(),
        BatotoDanish(),
        BatotoDutch(),
        BatotoEnglish(),
        BatotoFilipino(),
        BatotoFrench(),
        BatotoGerman(),
        BatotoGreek(),
        BatotoHebrew(),
        BatotoHungarian(),
        BatotoIndonesian(),
        BatotoItalian(),
        BatotoMalay(),
        BatotoPolish(),
        BatotoPortuguese(),
        BatotoRomanian(),
        BatotoRussian(),
        BatotoSpanish(),
        BatotoThai(),
        BatotoTurkish(),
        BatotoVietnamese()
        )
}

/**
 * Mangatensei is the base class
 * Use the OtherSite class for other sources
 */

class MangatenseiArabic : Mangatensei("ar", "arabic")
class MangatenseiBrazilian : Mangatensei("pt-BR", "brazilian")
class MangatenseiCzech : Mangatensei("cs", "czech")
class MangatenseiDanish : Mangatensei("da", "danish")
class MangatenseiDutch : Mangatensei("nl", "dutch")
class MangatenseiEnglish : Mangatensei("en", "english")
class MangatenseiFilipino : Mangatensei("fil", "filipino")
class MangatenseiFrench : Mangatensei("fr", "french")
class MangatenseiGerman : Mangatensei("de", "german")
class MangatenseiGreek : Mangatensei("el", "greek")
class MangatenseiHebrew : Mangatensei("iw", "hebrew")
class MangatenseiHungarian : Mangatensei("hu", "hungarian")
class MangatenseiIndonesian : Mangatensei("id", "indonesian")
class MangatenseiItalian : Mangatensei("it", "italian")
class MangatenseiMalay : Mangatensei("ms", "malay")
class MangatenseiPolish : Mangatensei("pl", "polish")
class MangatenseiPortuguese : Mangatensei("pt", "portuguese")
class MangatenseiRomanian : Mangatensei("ro", "romanian")
class MangatenseiRussian : Mangatensei("ru", "russian")
class MangatenseiSpanish : Mangatensei("es", "spanish")
class MangatenseiThai : Mangatensei("th", "thai")
class MangatenseiTurkish : Mangatensei("tr", "turkish")
class MangatenseiVietnamese : Mangatensei("vi", "vietnamese")

class MangawindowArabic : OtherSite("Mangawindow", "https://mangawindow.net", "ar", "arabic")
class MangawindowBrazilian : OtherSite("Mangawindow", "https://mangawindow.net", "pt-BR", "brazilian")
class MangawindowCzech : OtherSite("Mangawindow", "https://mangawindow.net", "cs", "czech")
class MangawindowDanish : OtherSite("Mangawindow", "https://mangawindow.net", "da", "danish")
class MangawindowDutch : OtherSite("Mangawindow", "https://mangawindow.net", "nl", "dutch")
class MangawindowEnglish : OtherSite("Mangawindow", "https://mangawindow.net", "en", "english")
class MangawindowFilipino : OtherSite("Mangawindow", "https://mangawindow.net", "fil", "filipino")
class MangawindowFrench : OtherSite("Mangawindow", "https://mangawindow.net", "fr", "french")
class MangawindowGerman : OtherSite("Mangawindow", "https://mangawindow.net", "de", "german")
class MangawindowGreek : OtherSite("Mangawindow", "https://mangawindow.net", "el", "greek")
class MangawindowHebrew : OtherSite("Mangawindow", "https://mangawindow.net", "iw", "hebrew")
class MangawindowHungarian : OtherSite("Mangawindow", "https://mangawindow.net", "hu", "hungarian")
class MangawindowIndonesian : OtherSite("Mangawindow", "https://mangawindow.net", "id", "indonesian")
class MangawindowItalian : OtherSite("Mangawindow", "https://mangawindow.net", "it", "italian")
class MangawindowMalay : OtherSite("Mangawindow", "https://mangawindow.net", "ms", "malay")
class MangawindowPolish : OtherSite("Mangawindow", "https://mangawindow.net", "pl", "polish")
class MangawindowPortuguese : OtherSite("Mangawindow", "https://mangawindow.net", "pt", "portuguese")
class MangawindowRomanian : OtherSite("Mangawindow", "https://mangawindow.net", "ro", "romanian")
class MangawindowRussian : OtherSite("Mangawindow", "https://mangawindow.net", "ru", "russian")
class MangawindowSpanish : OtherSite("Mangawindow", "https://mangawindow.net", "es", "spanish")
class MangawindowThai : OtherSite("Mangawindow", "https://mangawindow.net", "th", "thai")
class MangawindowTurkish : OtherSite("Mangawindow", "https://mangawindow.net", "tr", "turkish")
class MangawindowVietnamese : OtherSite("Mangawindow", "https://mangawindow.net", "vi", "vietnamese")

class BatotoArabic : OtherSite("Bato.to", "https://bato.to", "ar", "arabic")
class BatotoBrazilian : OtherSite("Bato.to", "https://bato.to", "pt-BR", "brazilian")
class BatotoCzech : OtherSite("Bato.to", "https://bato.to", "cs", "czech")
class BatotoDanish : OtherSite("Bato.to", "https://bato.to", "da", "danish")
class BatotoDutch : OtherSite("Bato.to", "https://bato.to", "nl", "dutch")
class BatotoEnglish : OtherSite("Bato.to", "https://bato.to", "en", "english")
class BatotoFilipino : OtherSite("Bato.to", "https://bato.to", "fil", "filipino")
class BatotoFrench : OtherSite("Bato.to", "https://bato.to", "fr", "french")
class BatotoGerman : OtherSite("Bato.to", "https://bato.to", "de", "german")
class BatotoGreek : OtherSite("Bato.to", "https://bato.to", "el", "greek")
class BatotoHebrew : OtherSite("Bato.to", "https://bato.to", "iw", "hebrew")
class BatotoHungarian : OtherSite("Bato.to", "https://bato.to", "hu", "hungarian")
class BatotoIndonesian : OtherSite("Bato.to", "https://bato.to", "id", "indonesian")
class BatotoItalian : OtherSite("Bato.to", "https://bato.to", "it", "italian")
class BatotoMalay : OtherSite("Bato.to", "https://bato.to", "ms", "malay")
class BatotoPolish : OtherSite("Bato.to", "https://bato.to", "pl", "polish")
class BatotoPortuguese : OtherSite("Bato.to", "https://bato.to", "pt", "portuguese")
class BatotoRomanian : OtherSite("Bato.to", "https://bato.to", "ro", "romanian")
class BatotoRussian : OtherSite("Bato.to", "https://bato.to", "ru", "russian")
class BatotoSpanish : OtherSite("Bato.to", "https://bato.to", "es", "spanish")
class BatotoThai : OtherSite("Bato.to", "https://bato.to", "th", "thai")
class BatotoTurkish : OtherSite("Bato.to", "https://bato.to", "tr", "turkish")
class BatotoVietnamese : OtherSite("Bato.to", "https://bato.to", "vi", "vietnamese")


