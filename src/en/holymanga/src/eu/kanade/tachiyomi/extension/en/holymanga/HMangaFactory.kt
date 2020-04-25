package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HolyManga(),
        HeavenManga()
    )
}

class HolyManga : HManga("HolyManga", "http://w12.holymanga.net")
class HeavenManga : HManga("HeavenManga", "http://ww8.heavenmanga.org")
