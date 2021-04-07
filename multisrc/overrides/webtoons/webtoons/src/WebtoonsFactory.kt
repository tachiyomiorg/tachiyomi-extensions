package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.multisrc.webtoons.Webtoons
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Calendar

class WebtoonsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WebtoonsEN(),
        WebtoonsID(),
        WebtoonsTH(),
        WebtoonsES(),
        WebtoonsFR(),
        WebtoonsZH(),
    )

}
class WebtoonsEN : Webtoons("Webtoons", "https://www.webtoons.com", "en")
class WebtoonsID : Webtoons("Webtoons", "https://www.webtoons.com", "id") {
    // Override ID as part of the name was removed to be more consiten with other enteries
    override val id: Long = 8749627068478740298
    
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
class WebtoonsTH : Webtoons("Webtoons", "https://www.webtoons.com", "th", dateFormat = SimpleDateFormat("d MMM yyyy", Locale("th")))
class WebtoonsES : Webtoons("Webtoons", "https://www.webtoons.com", "es") {
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
class WebtoonsFR : Webtoons("Webtoons", "https://www.webtoons.com", "fr", dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRENCH))
class WebtoonsZH : Webtoons("Webtoons", "https://www.webtoons.com", "zh", "zh-hant", "zh_TW", SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE))
