package eu.kanade.tachiyomi.extension.all.luscious

import eu.kanade.tachiyomi.multisrc.luscious.Luscious
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LusciousFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LusciousEN(),
        LusciousJA(),
        LusciousES(),
        LusciousIT(),
        LusciousDE(),
        LusciousFR(),
        LusciousZH(),
        LusciousKO(),
        LusciousOTHER(),
        LusciousPT(),
        LusciousTH(),
        LusciousALL(),
    )
}
class LusciousEN : Luscious("Luscious", "https://www.luscious.net", "en")
class LusciousJA : Luscious("Luscious", "https://www.luscious.net", "ja")
class LusciousES : Luscious("Luscious", "https://www.luscious.net", "es")
class LusciousIT : Luscious("Luscious", "https://www.luscious.net", "it")
class LusciousDE : Luscious("Luscious", "https://www.luscious.net", "de")
class LusciousFR : Luscious("Luscious", "https://www.luscious.net", "fr")
class LusciousZH : Luscious("Luscious", "https://www.luscious.net", "zh")
class LusciousKO : Luscious("Luscious", "https://www.luscious.net", "ko")
class LusciousOTHER : Luscious("Luscious", "https://www.luscious.net", "other")
class LusciousPT : Luscious("Luscious", "https://www.luscious.net", "pt")
class LusciousTH : Luscious("Luscious", "https://www.luscious.net", "th")
class LusciousALL : Luscious("Luscious", "https://www.luscious.net", "all")
