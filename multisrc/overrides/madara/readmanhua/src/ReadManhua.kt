package eu.kanade.tachiyomi.extension.en.readmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.WordSet
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReadManhua : Madara(
    "ReadManhua",
    "https://readmanhua.net",
    "en",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
) {

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val year = Calendar.getInstance().get(Calendar.YEAR).toLong()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.text()
            }

            // Dates can be part of a "new" graphic or plain text
            chapter.date_upload = select("img").firstOrNull()?.attr("alt")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(select("span.chapter-release-date i").firstOrNull()?.text() + " " + year)
        }

        return chapter
    }

    // Parses dates in this form:
    // 21 horas ago
    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("hari", "gün", "jour", "día", "dia", "day").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("jam", "saat", "heure", "hora", "hour").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("menit", "dakika", "min", "minute", "minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("detik", "segundo", "second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }
}
