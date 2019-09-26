package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.util.*

class ToomicsFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllToomics()
}

class ToomicsEnglish : ToomicsGlobal("en", "MMM dd, yyyy", Locale.ENGLISH)
class ToomicsSimplifiedChinese : ToomicsGlobal("sc", "yyyy.MM.dd", Locale.SIMPLIFIED_CHINESE, "zh", "简体")
class ToomicsTraditionalChinese : ToomicsGlobal("tc", "yyyy.MM.dd", Locale.TRADITIONAL_CHINESE, "zh", "繁體")
class ToomicsSpanishLA : ToomicsGlobal("mx", "d MMM, yyyy", Locale("es", "419"), "es", "LA")
class ToomicsSpanish : ToomicsGlobal("es", "d MMM, yyyy", Locale("es", "419"), "es", "ES")
class ToomicsItalian : ToomicsGlobal("it", "d MMM, yyyy", Locale.ITALIAN)
class ToomicsGerman : ToomicsGlobal("de", "d. MMM yyyy", Locale.GERMAN)
class ToomicsFrench : ToomicsGlobal("fr", "dd MMM. yyyy", Locale.ENGLISH)
class ToomicsPortuguese : ToomicsGlobal("por", "d 'de' MMM 'de' yyyy", Locale("pt", "BR"), "pt")

fun getAllToomics(): List<Source> = listOf(
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
