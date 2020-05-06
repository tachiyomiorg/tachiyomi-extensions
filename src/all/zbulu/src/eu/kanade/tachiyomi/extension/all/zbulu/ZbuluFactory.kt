package eu.kanade.tachiyomi.extension.all.zbulu

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ZbuluFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HolyManga(),
        HeavenManga(),
        KooManga()
    )
}

class HolyManga : Zbulu("HolyManga", "https://w15.holymanga.net")
class HeavenManga : Zbulu("HeavenManga", "http://heaventoon.com")
class KooManga : Zbulu("Koo Manga", "http://ww1.koomanga.com")
