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
        MangatenseiVietnamese()
    )
}

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
