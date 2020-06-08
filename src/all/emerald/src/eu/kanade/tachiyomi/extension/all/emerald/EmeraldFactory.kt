package eu.kanade.tachiyomi.extension.all.emerald

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class EmeraldFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map { Mangawindow(it.first, it.second) } + languages.map { Batoto(it.first, it.second) }
}

class Mangawindow(tachiLang: String, siteLang: String)
    : Emerald("Mangawindow", "https://mangawindow.net", tachiLang, siteLang)

class Batoto(tachiLang: String, siteLang: String)
    : Emerald("Bato.to", "https://bato.to", tachiLang, siteLang)

private val languages = listOf(
    Pair("ar", "arabic"),
    Pair("pt-BR", "brazilian"),
    Pair("cs", "czech"),
    Pair("da", "danish"),
    Pair("nl", "dutch"),
    Pair("en", "english"),
    Pair("fil", "filipino"),
    Pair("fr", "french"),
    Pair("de", "german"),
    Pair("el", "greek"),
    Pair("iw", "hebrew"),
    Pair("hu", "hungarian"),
    Pair("id", "indonesian"),
    Pair("it", "italian"),
    Pair("ms", "malay"),
    Pair("pl", "polish"),
    Pair("pt", "portuguese"),
    Pair("ro", "romanian"),
    Pair("ru", "russian"),
    Pair("es", "spanish"),
    Pair("th", "thai"),
    Pair("tr", "turkish"),
    Pair("vi", "vietnamese")
)
