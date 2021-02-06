package eu.kanade.tachiyomi.extension.all.batoto

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class BatoToFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map { BatoTo(it.first, it.second) }
}

private val languages = listOf(
    Pair("ar", "ar"),
    Pair("bg", "bg"),
    Pair("cs", "cs"),
    Pair("da", "da"),
    Pair("de", "de"),
    Pair("el", "el"),
    Pair("en", "en"),
    Pair("es", "es"),
    Pair("es-419", "es_419"),
    Pair("eu", "eu"),
    Pair("fa", "fa"),
    Pair("fi", "fi"),
    Pair("fil", "fil"),
    Pair("fr", "fr"),
    Pair("he", "he"),
    Pair("hi", "hi"),
    Pair("hr", "hr"),
    Pair("hu", "hu"),
    Pair("id", "id"),
    Pair("it", "it"),
    Pair("ja", "ja"),
    Pair("ko", "ko"),
    Pair("ku", "ku"),
    Pair("ml", "ml"),
    Pair("mn", "mn"),
    Pair("ms", "ms"),
    Pair("my", "my"),
    Pair("nl", "nl"),
    Pair("no", "no"),
    Pair("pl", "pl"),
    Pair("pt", "pt"),
    Pair("pt-BR", "pt_br"),
    Pair("pt-PT", "pt_pt"),
    Pair("ro", "ro"),
    Pair("ru", "ru"),
    Pair("th", "th"),
    Pair("tr", "tr"),
    Pair("uk", "uk"),
    Pair("vi", "vi"),
    Pair("xh", "xh"),
    Pair("zh", "zh"),
    Pair("zh-rHK", "zh_hk"),
    Pair("zh-rTW", "zh_tw"),
    Pair("zu", "zu"),
)
