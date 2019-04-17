package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NineMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllNineManga()
}

fun getAllNineManga(): List<Source> {
    return listOf(
        EsNineManga(),
        EnNineManga()
    )
}

class EsNineManga : NineManga("EsNineManga", "http://es.ninemanga.com", "es")

class EnNineManga : NineManga("EnNineManga", "http://en.ninemanga.com", "en")