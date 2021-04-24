package eu.kanade.tachiyomi.extension.es.samuraiscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SamuraiScan : Madara("SamuraiScan", "https://samuraiscan.com", "es", SimpleDateFormat("MMMM d, yyyy", Locale("es"))) {
    override fun getGenreList() = listOf(
        Genre("Acción", "accion"),
        Genre("Artes Marciales", "artes-marciales"),
        Genre("Aventura", "aventura"),
        Genre("Drama", "drama"),
        Genre("Fantasia", "fantasia"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Magia", "magia"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Psicológico", "psicologico"),
        Genre("Reencarnación", "reencarnacion"),
        Genre("Romance", "romance"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Tragedia", "tragedia"),
        Genre("Wuxia", "wuxia")
    )
}
