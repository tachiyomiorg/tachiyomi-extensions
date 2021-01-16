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
        WebtoonsFr(),
        WebtoonsEs(),
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
        WebtoonsTranslate("uk", "UKR"),
        WebtoonsTranslate("es", "SPA")
    )
}

class WebtoonsEnglish : WebtoonsDefault("en")

class WebtoonsIndonesian : WebtoonsDefault("id") {
    override val name: String = "Webtoons.com (Indonesian)"

    // Android seems to be unable to parse Indonesian dates; we'll use a short hard-coded table
    // instead.
    private val dateMap: Array<String> = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    override fun chapterParseDate(date: String): Long {
        val expr = Regex("""(\d{4}) ([A-Z][a-z]{2}) (\d+)""").find(date) ?: return 0
        val (_, year, monthString, day) = expr.groupValues
        val monthIndex = dateMap.indexOf(monthString)
        return GregorianCalendar(year.toInt(), monthIndex, day.toInt()).time.time
    }
}

class WebtoonsThai : WebtoonsDefault("th", dateFormat = SimpleDateFormat("d MMM yyyy", Locale("th")))

class WebtoonsChineseTraditional : WebtoonsDefault("zh", "zh-hant", "zh_TW", SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE))

class WebtoonsFr : WebtoonsDefault("fr", dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRENCH))

class WebtoonsEs : WebtoonsDefault("es") {
    // Android seems to be unable to parse es dates like Indonesian; we'll use a short hard-coded table
    // instead.
    private val dateMap: Array<String> = arrayOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    override fun chapterParseDate(date: String): Long {
        val expr = Regex("""(\d+)-([a-z]{3})-(\d{4})""").find(date) ?: return 0
        val (_, day, monthString, year) = expr.groupValues
        val monthIndex = dateMap.indexOf(monthString)
        return GregorianCalendar(year.toInt(), monthIndex, day.toInt()).time.time
    }
}
