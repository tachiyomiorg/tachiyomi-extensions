package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.*

class ToomicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ToomicsEnglish(),
        ToomicsSimplifiedChinese(),
        ToomicsTraditionalChinese(),
        ToomicsSpanishLA(),
        ToomicsSpanish(),
        ToomicsItalian(),
        ToomicsGerman(),
        ToomicsFrench(),
        ToomicsPortuguese()
    )
}

class ToomicsEnglish : ToomicsGlobal("en", SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH))
class ToomicsSimplifiedChinese : ToomicsGlobal("sc", SimpleDateFormat("yyyy.MM.dd", Locale.SIMPLIFIED_CHINESE), "zh", "简体")
class ToomicsTraditionalChinese : ToomicsGlobal("tc", SimpleDateFormat("yyyy.MM.dd", Locale.TRADITIONAL_CHINESE), "zh", "繁體")
class ToomicsSpanishLA : ToomicsGlobal("mx", SimpleDateFormat("d MMM, yyyy", Locale("es", "419")), "es", "LA")
class ToomicsSpanish : ToomicsGlobal("es", SimpleDateFormat("d MMM, yyyy", Locale("es", "419")), "es")
class ToomicsItalian : ToomicsGlobal("it", SimpleDateFormat("d MMM, yyyy", Locale.ITALIAN))
class ToomicsGerman : ToomicsGlobal("de", SimpleDateFormat("d. MMM yyyy", Locale.GERMAN))
class ToomicsFrench : ToomicsGlobal("fr", SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH))
class ToomicsPortuguese : ToomicsGlobal("por", SimpleDateFormat("d 'de' MMM 'de' yyyy", Locale("pt", "BR")), "pt")
