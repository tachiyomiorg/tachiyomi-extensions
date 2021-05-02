package eu.kanade.tachiyomi.extension.fr.lecercleduscan

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide

class LeCercleDuScan : FoolSlide("Le Cercle du Scan", "https://lel.lecercleduscan.com", "fr") {

    override fun parseChapterDate(date: String): Long? {
        val dateToEnglish = when (val lcDate = date.toLowerCase()) {
            "hier" -> "yesterday"
            "aujourd'hui" -> "today"
            "demain" -> "tomorrow"

            else -> lcDate
        }

        return super.parseChapterDate(dateToEnglish)
    }
}
