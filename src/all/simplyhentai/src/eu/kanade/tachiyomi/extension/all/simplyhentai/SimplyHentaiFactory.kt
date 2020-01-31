package eu.kanade.tachiyomi.extension.all.simplyhentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class SimplyHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        SimplyHentaiEN(),
        SimplyHentaiJA(),
        SimplyHentaiZH(),
        SimplyHentaiKO(),
        SimplyHentaiES(),
        SimplyHentaiRU(),
        SimplyHentaiFR(),
        SimplyHentaiDE(),
        SimplyHentaiPT()
        )
}

class SimplyHentaiEN: SimplyHentai("en", "english", "1")
class SimplyHentaiJA: SimplyHentai("ja", "japanese", "2")
class SimplyHentaiZH: SimplyHentai("zh", "chinese", "11")
class SimplyHentaiKO: SimplyHentai("ko", "korean", "9")
class SimplyHentaiES: SimplyHentai("es", "spanish", "8")
class SimplyHentaiRU: SimplyHentai("ru", "russian", "7")
class SimplyHentaiFR: SimplyHentai("fr", "french", "3")
class SimplyHentaiDE: SimplyHentai("de", "german", "4")
class SimplyHentaiPT: SimplyHentai("pt", "portuguese", "6")

