package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale

class WebtoonsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsEnglish(),
        WebtoonsChineseTraditional(),
        WebtoonsIndonesian(),
        WebtoonsThai(),
        DongmanManhua(),

        // Fan translations
        WebtoonsTranslate("en", "ENG"),
        WebtoonsTranslate("zh", "CMN", " (Simplified)"),
        WebtoonsTranslate("zh", "CMT", " (Traditional)"),
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

class WebtoonsEnglish : WebtoonsDefault("en")

class WebtoonsIndonesian: WebtoonsDefault("in", "id") {
    override val name: String = "Webtoons.com (Indonesian)"

    // Android seems to be unable to parse Indonesian dates; we'll use a short hard-coded table
    // instead.
    private val DATE_MAP: Array<String> = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

    override fun chapterParseDate(date: String): Long {
        val expr = Regex("""(\d{4}) ([A-Z][a-z]{2}) (\d{1,})""").find(date) ?: return 0
        val (_, year, monthString, day) = expr.groupValues
        val monthIndex = DATE_MAP.indexOf(monthString)
        return GregorianCalendar(year.toInt(), monthIndex, day.toInt()).time.time
    }
}

class WebtoonsThai: WebtoonsDefault("th") {
    override fun chapterParseDate(date: String): Long {
        return SimpleDateFormat("d MMM yyyy", Locale("th")).parse(date).time
    }
}

class WebtoonsChineseTraditional: WebtoonsDefault("zh", "zh-hant", "zh_TW") {
    override fun chapterParseDate(date: String): Long {
        return SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE).parse(date).time
    }
}
