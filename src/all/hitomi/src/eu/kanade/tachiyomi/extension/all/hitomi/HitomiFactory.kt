package eu.kanade.tachiyomi.extension.all.hitomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HitomiFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList
        .filterNot { it.first.isEmpty() }
        .map { Hitomi(it.first, it.second) }
}

/**
 * These should all be valid languages but I was too lazy to look up all the language codes
 * Replace an empty string with a valid language code to enable that language
 */
private val languageList = listOf(
    Pair("other", "all"), // all languages
    Pair("id", "indonesian"),
    Pair("", "catalan"),
    Pair("", "cebuano"),
    Pair("", "czech"),
    Pair("", "danish"),
    Pair("de", "german"),
    Pair("", "estonian"),
    Pair("en", "english"),
    Pair("es", "spanish"),
    Pair("", "esperanto"),
    Pair("fr", "french"),
    Pair("it", "italian"),
    Pair("", "latin"),
    Pair("", "hungarian"),
    Pair("", "dutch"),
    Pair("", "norwegian"),
    Pair("pl", "polish"),
    Pair("pt-BR", "portuguese"),
    Pair("", "romanian"),
    Pair("", "albanian"),
    Pair("", "slovak"),
    Pair("", "finnish"),
    Pair("", "swedish"),
    Pair("", "tagalog"),
    Pair("vi", "vietnamese"),
    Pair("tr", "turkish"),
    Pair("", "greek"),
    Pair("", "mongolian"),
    Pair("ru", "russian"),
    Pair("", "ukrainian"),
    Pair("", "hebrew"),
    Pair("ar", "arabic"),
    Pair("", "persian"),
    Pair("th", "thai"),
    Pair("ko", "korean"),
    Pair("zh", "chinese"),
    Pair("ja", "japanese")
)
