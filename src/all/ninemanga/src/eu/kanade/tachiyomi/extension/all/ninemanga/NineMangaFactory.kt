package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NineMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllNineManga()
}

fun getAllNineManga(): List<Source> {
    return listOf(
        EsNineManga(),
        BrNineManga(),
        EnNineManga(),
        RuNineManga(),
        DeNineManga(),
        ItNineManga(),
        FrNineManga()
    )
}

class EsNineManga : NineManga("EsNineManga", "http://es.ninemanga.com", "es")

class BrNineManga : NineManga("BrNineManga", "http://br.ninemanga.com", "br")

class EnNineManga : NineManga("EnNineManga", "http://en.ninemanga.com", "en")

class RuNineManga : NineManga("RuNineManga", "http://ru.ninemanga.com", "ru")

class DeNineManga : NineManga("DeNineManga", "http://de.ninemanga.com", "de")

class ItNineManga : NineManga("ItNineManga", "http://it.ninemanga.com", "it")

class FrNineManga : NineManga("FrNineManga", "http://fr.ninemanga.com", "fr")

