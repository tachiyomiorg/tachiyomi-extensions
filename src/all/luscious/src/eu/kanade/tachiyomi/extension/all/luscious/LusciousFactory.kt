package eu.kanade.tachiyomi.extension.all.luscious

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class LusciousFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        Luscious("en", Luscious.ENGLISH_LUS_LANG_VAL),
        Luscious("ja", Luscious.JAPANESE_LUS_LANG_VAL),
        Luscious("es", Luscious.SPANISH_LUS_LANG_VAL),
        Luscious("it", Luscious.ITALIAN_LUS_LANG_VAL),
        Luscious("de", Luscious.GERMAN_LUS_LANG_VAL),
        Luscious("fr", Luscious.FRENCH_LUS_LANG_VAL),
        Luscious("zh", Luscious.CHINESE_LUS_LANG_VAL),
        Luscious("ko", Luscious.KOREAN_LUS_LANG_VAL),
        Luscious("other", Luscious.OTHERS_LUS_LANG_VAL),
        Luscious("pt", Luscious.PORTUGESE_LUS_LANG_VAL),
        Luscious("th", Luscious.THAI_LUS_LANG_VAL)
    )
}
