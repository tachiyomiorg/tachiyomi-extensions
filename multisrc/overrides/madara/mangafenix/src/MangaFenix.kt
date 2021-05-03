package eu.kanade.tachiyomi.extension.es.mangafenix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFenix : Madara("Manga Fenix", "https://manga-fenix.com", "es", SimpleDateFormat("dd MMMM, yyyy", Locale("es"))){
  override fun getGenreList(): List<Genre> = listOf(
        Genre("Accion", "accion"),
        Genre("Artes Marciales", "artes-marciales"),
        Genre("Aventuras", "aventuras"),
        Genre("Ciencia Ficción", "ciencia-ficcion"),
        Genre("Comédia", "comedia"),
        Genre("Cultivacion", "cultivacion"),
        Genre("Drama", "drama"),
        Genre("Fantasia", "fantasia"),
        Genre("Haren", "haren"),
        Genre("Manhua", "manhua"),
        Genre("Puto Amo", "puto-amo"),
        Genre("Reencarnacion", "reencarnacion"),
        Genre("Romance", "romance"),
        Genre("Seinen", "seinen"),
        Genre("Shounen", "shounen"),
        Genre("Terror", "terror"),
    )
}
