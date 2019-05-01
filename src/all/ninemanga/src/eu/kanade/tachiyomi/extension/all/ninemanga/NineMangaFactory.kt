package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class NineMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllNineManga()
}

fun getAllNineManga(): List<Source> {
    return listOf(
        NineMangaEn(),
        NineMangaEs(),
        NineMangaBr(),
        NineMangaRu(),
        NineMangaDe(),
        NineMangaIt(),
        NineMangaFr()
    )
}

class NineMangaEn : NineManga("NineMangaEn", "http://en.ninemanga.com", "en")

class NineMangaEs : NineManga("NineMangaEs", "http://es.ninemanga.com", "es") {
    override fun parseStatus(status: String) = when {
        status.contains("En curso") -> SManga.ONGOING
        status.contains("Completado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            if(dateWords[1].contains(",")){
                try {
                    return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
                } catch (e: ParseException) {
                    return 0L
                }
            }else{
                val timeAgo = Integer.parseInt(dateWords[0])
                return Calendar.getInstance().apply {
                    when (dateWords[1]) {
                        "minutos" -> Calendar.MINUTE
                        "horas" -> Calendar.HOUR
                        else -> null
                    }?.let {
                        add(it, -timeAgo)
                    }
                }.timeInMillis
            }
        }
        return 0L
    }
}

class NineMangaBr : NineManga("NineMangaBr", "http://br.ninemanga.com", "br") {
    override fun parseStatus(status: String) = when {
        status.contains("Em tradução") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            if(dateWords[1].contains(",")){
                try {
                    return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
                } catch (e: ParseException) {
                    return 0L
                }
            }else{
                val timeAgo = Integer.parseInt(dateWords[0])
                return Calendar.getInstance().apply {
                    when (dateWords[1]) {
                        "minutos" -> Calendar.MINUTE
                        "hora" -> Calendar.HOUR
                        else -> null
                    }?.let {
                        add(it, -timeAgo)
                    }
                }.timeInMillis
            }
        }
        return 0L
    }
}

class NineMangaRu : NineManga("NineMangaRu", "http://ru.ninemanga.com", "ru") {
    override fun parseStatus(status: String) = when {
        // No Ongoing status
        status.contains("завершенный") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            if(dateWords[1].contains(",")){
                try {
                    return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
                } catch (e: ParseException) {
                    return 0L
                }
            }else{
                val timeAgo = Integer.parseInt(dateWords[0])
                return Calendar.getInstance().apply {
                    when (dateWords[1]) {
                        "минут" -> Calendar.MINUTE
                        "часа" -> Calendar.HOUR
                        else -> null
                    }?.let {
                        add(it, -timeAgo)
                    }
                }.timeInMillis
            }
        }
        return 0L
    }
}

class NineMangaDe : NineManga("NineMangaDe", "http://de.ninemanga.com", "de") {
    override fun parseStatus(status: String) = when {
        status.contains("Laufende") -> SManga.ONGOING
        status.contains("Abgeschlossen") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            try {
                return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) {
                return 0L
            }
        }
        else if (dateWords.size == 2) { // Aleman
            val timeAgo = Integer.parseInt(dateWords[0])
            return Calendar.getInstance().apply {
                when (dateWords[1]) {
                    "Stunden" -> Calendar.HOUR // Aleman - 2 palabras
                    else -> null
                }?.let {
                    add(it, -timeAgo)
                }
            }.timeInMillis
        }
        return 0L
    }
}

class NineMangaIt : NineManga("NineMangaIt", "http://it.ninemanga.com", "it") {
    override fun parseStatus(status: String) = when {
        status.contains("In corso") -> SManga.ONGOING
        status.contains("Completato") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            if(!dateWords[1].contains(",")){
                try {
                    return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
                } catch (e: ParseException) {
                    return 0L
                }
            }else{
                val timeAgo = Integer.parseInt(dateWords[0])
                return Calendar.getInstance().apply {
                    when (dateWords[1]) {
                        "minuti" -> Calendar.MINUTE
                        "ore" -> Calendar.HOUR
                        else -> null
                    }?.let {
                        add(it, -timeAgo)
                    }
                }.timeInMillis
            }
        }
        return 0L
    }
}

class NineMangaFr : NineManga("NineMangaFr", "http://fr.ninemanga.com", "fr") {
    override fun parseStatus(status: String) = when {
        status.contains("En cours") -> SManga.ONGOING
        status.contains("Complété") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            try {
                return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
            } catch (e: ParseException) {
                return 0L
            }
        }

        else if(dateWords.size == 5) {
            val timeAgo = Integer.parseInt(dateWords[3])
            return Calendar.getInstance().apply {
                when (dateWords[4]) {
                    "minutes" -> Calendar.MINUTE
                    "heures" -> Calendar.HOUR
                    else -> null
                }?.let {
                    add(it, -timeAgo)
                }
            }.timeInMillis
        }
        return 0L
    }
}

