package eu.kanade.tachiyomi.extension.all.hitomi

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class HitomiFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList
        .map { Hitomi(it.first, it.second) }
}

private val languageList = listOf(
    Pair("other", "all"), // all languages
    Pair("id", "indonesian"),
    Pair("ca", "catalan"),
    Pair("ceb", "cebuano"),
    Pair("cs", "czech"),
    Pair("da", "danish"),
    Pair("de", "german"),
    Pair("et", "estonian"),
    Pair("en", "english"),
    Pair("es", "spanish"),
    Pair("eo", "esperanto"),
    Pair("fr", "french"),
    Pair("it", "italian"),
    Pair("la", "latin"),
    Pair("hu", "hungarian"),
    Pair("nl", "dutch"),
    Pair("no", "norwegian"),
    Pair("pl", "polish"),
    Pair("pt", "portuguese"),
    Pair("ro", "romanian"),
    Pair("sq", "albanian"),
    Pair("sk", "slovak"),
    Pair("fi", "finnish"),
    Pair("sv", "swedish"),
    Pair("tl", "tagalog"),
    Pair("vi", "vietnamese"),
    Pair("tr", "turkish"),
    Pair("el", "greek"),
    Pair("mn", "mongolian"),
    Pair("ru", "russian"),
    Pair("uk", "ukrainian"),
    Pair("he", "hebrew"),
    Pair("ar", "arabic"),
    Pair("fa", "persian"),
    Pair("th", "thai"),
    Pair("ko", "korean"),
    Pair("zh", "chinese"),
    Pair("ja", "japanese")
)
