package eu.kanade.tachiyomi.extension.en.ksgroupscans

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.util.Calendar


class KSGroupScans : FMReader("KSGroupScans", "https://ksgroupscans.com", "en") {
    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter {
        return SChapter.create().apply {
            element.select(chapterUrlSelector).first().let {
                setUrlWithoutDomain(it.attr("abs:href"))
                name = element.select(".chapter-name").text()
            }
            date_upload = element.select(chapterTimeSelector).let { if (it.hasText()) parseChapterDate(it.text()) else 0 }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val dateWord = date.split(' ')[dateWordIndex].let {
            if (it.contains("(")) {
                it.substringBefore("(")
            } else {
                it.substringBefore("s")
            }
        }

        // languages: en, vi, es, tr
        return when (dateWord) {
            "min", "minute", "phút", "minuto", "dakika" -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "hour", "giờ", "hora", "saat" -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "day", "ngày", "día", "gün" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "week", "tuần", "semana", "hafta" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "month", "tháng", "mes", "ay" -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "year", "năm", "año", "yıl" -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }
}
