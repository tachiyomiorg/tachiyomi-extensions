package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsEnglish(),
        WebtoonsChineseTraditional(),
        WebtoonsIndonesian(),
        WebtoonsThai(),
        WebtoonsFr(),
        DongmanManhua(),

        // Fan translations
        WebtoonsTranslate("en", "ENG"),
        WebtoonsTranslate("zh", "CMN", " (Simplified)"),
        WebtoonsTranslate("zh", "CMT", " (Traditional)"),
        WebtoonsTranslate("th", "THA"),
        WebtoonsTranslate("id", "IND"),
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

class WebtoonsEnglish : WebtoonsDefault("en")

class WebtoonsIndonesian : WebtoonsDefault("id", dateFormat = SimpleDateFormat("yyyy MMM dd", Locale("id"))) {
    override val name: String = "Webtoons.com (Indonesian)"
}

class WebtoonsThai : WebtoonsDefault("th", dateFormat = SimpleDateFormat("d MMM yyyy", Locale("th")))

class WebtoonsChineseTraditional : WebtoonsDefault("zh", "zh-hant", "zh_TW", SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE))

class WebtoonsFr : WebtoonsDefault("fr", dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRENCH))
