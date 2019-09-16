package eu.kanade.tachiyomi.extension.id.webtoons

import eu.kanade.tachiyomi.extension.all.webtoons.WebtoonsDefault
import java.util.*

class WebtoonsIndonesian: WebtoonsDefault("en", "id") {
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
