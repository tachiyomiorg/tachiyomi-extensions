package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.extension.en.webtoons.WebtoonsEnglish
import eu.kanade.tachiyomi.extension.id.webtoons.WebtoonsIndonesian
import eu.kanade.tachiyomi.extension.th.webtoons.WebtoonsThai
import eu.kanade.tachiyomi.extension.zh.webtoons.WebtoonsChineseTraditional
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WebtoonsFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllWebtoons()
}

fun getAllWebtoons(): List<Source> {
    return listOf(
        WebtoonsEnglish(),
        WebtoonsChineseTraditional(),
        WebtoonsIndonesian(),
        WebtoonsThai(),

        // fan translations
        WebtoonsTranslate("en", "ENG"),
        WebtoonsTranslate("zh", "CMN", " (Simplified)"),
        WebtoonsTranslate("zh", "CHT", " (Traditional)"),
        WebtoonsTranslate("th", "THA"),
        WebtoonsTranslate("in", "IND"),
        WebtoonsTranslate("fr", "FRA"),
        WebtoonsTranslate("vi", "VIE"),
        WebtoonsTranslate("ru", "RUS"),
        WebtoonsTranslate("ar", "ARA"),
        WebtoonsTranslate("fil", "FIL"),
        WebtoonsTranslate("de", "DEU"),
        WebtoonsTranslate("hi", "HIN"),
        WebtoonsTranslate("it", "ITA"),
        WebtoonsTranslate("ja", "JPN"),
        WebtoonsTranslate("pt", "POR", " (Brazilian)"),
        WebtoonsTranslate("tr", "TUR"),
        WebtoonsTranslate("ms", "MAY"),
        WebtoonsTranslate("pl", "POL"),
        WebtoonsTranslate("pt", "POT", " (European)"),
        WebtoonsTranslate("bg", "BUL"),
        WebtoonsTranslate("da", "DAN"),
        WebtoonsTranslate("nl", "NLD"),
        WebtoonsTranslate("ro", "RON"),
        WebtoonsTranslate("mn", "MON"),
        WebtoonsTranslate("el", "GRE"),
        WebtoonsTranslate("lt", "LIT"),
        WebtoonsTranslate("cs", "CES"),
        WebtoonsTranslate("sv", "SWE"),
        WebtoonsTranslate("bn", "BEN"),
        WebtoonsTranslate("fa", "PER"),
        WebtoonsTranslate("uk", "UKR")
    )
}
