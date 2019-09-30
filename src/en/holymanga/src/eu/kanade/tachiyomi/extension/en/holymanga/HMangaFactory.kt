package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HolyManga(),
        HeavenManga()
    )
}

class HolyManga : HManga("HolyManga", "https://holymanga.net")
class HeavenManga : HManga("HeavenManga", "https://heavenmanga.org")

