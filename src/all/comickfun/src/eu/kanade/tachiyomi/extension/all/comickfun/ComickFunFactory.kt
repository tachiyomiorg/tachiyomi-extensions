package eu.kanade.tachiyomi.extension.all.comickfun

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

val toISO639 = mapOf(
    "gb" to "en", // English
    "br" to "pt-BR", // Brazillian Portugese
    "mx" to "es-419", // Latin-American Spanish
    "vn" to "vi", // Vietnemese
    "hk" to "zh-Hant", // Traditional Chinese,
    "cn" to "zh-Hans", // Simplified Chinese
    "sa" to "ar", // Arabic
    "ct" to "ca", // Catalan; Valencian
    "ir" to "fa", // Persian
    "ua" to "uk", // Ukranian
    "il" to "he", // hebrew
    "my" to "ms", // Malay
    "ph" to "tl", // Filipino
    "jp" to "ja", // Japanese
    "in" to "hi", // Hindi
    "kr" to "ko", // Korean
    "cz" to "cs", // Czech
    "bd" to "bn", // Bengali
    "gr" to "el", // Modern Greek
    "rs" to "sr", // Serbo-Croatian
    "dk" to "da", // Danish

).withDefault { it } // country code matches language code

@Nsfw
class ComickFunFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        "all",
        "gb",
        "br",
        "ru",
        "fr",
        "mx",
        "pl",
        "tr",
        "it",
        "es",
        "id",
        "hu",
        "vn",
        "hk",
        "sa",
        "de",
        "cn",
        "ct",
        "bg",
        "th",
        "ir",
        "ua",
        "mn",
        "ro",
        "il",
        "my",
        "ph",
        "jp",
        "in",
        "mm",
        "kr",
        "cz",
        "pt",
        "nl",
        "se",
        "bd",
        "no",
        "lt",
        "gr",
        "rs",
        "dk"
    ).map { object : ComickFun(toISO639.getValue(it), it) {} }
}
