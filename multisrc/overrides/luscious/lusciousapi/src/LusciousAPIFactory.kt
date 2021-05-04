package eu.kanade.tachiyomi.extension.all.lusciousapi

import eu.kanade.tachiyomi.multisrc.luscious.Luscious
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LusciousAPIFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LusciousAPIEN(),
        LusciousAPIJA(),
        LusciousAPIES(),
        LusciousAPIIT(),
        LusciousAPIDE(),
        LusciousAPIFR(),
        LusciousAPIZH(),
        LusciousAPIKO(),
        LusciousAPIOTHER(),
        LusciousAPIPT(),
        LusciousAPITH(),
        LusciousAPIALL(),
    )
}
class LusciousAPIEN : Luscious("Luscious (API)", "https://api.luscious.net", "en")
class LusciousAPIJA : Luscious("Luscious (API)", "https://api.luscious.net", "ja")
class LusciousAPIES : Luscious("Luscious (API)", "https://api.luscious.net", "es")
class LusciousAPIIT : Luscious("Luscious (API)", "https://api.luscious.net", "it")
class LusciousAPIDE : Luscious("Luscious (API)", "https://api.luscious.net", "de")
class LusciousAPIFR : Luscious("Luscious (API)", "https://api.luscious.net", "fr")
class LusciousAPIZH : Luscious("Luscious (API)", "https://api.luscious.net", "zh")
class LusciousAPIKO : Luscious("Luscious (API)", "https://api.luscious.net", "ko")
class LusciousAPIOTHER : Luscious("Luscious (API)", "https://api.luscious.net", "other")
class LusciousAPIPT : Luscious("Luscious (API)", "https://api.luscious.net", "pt")
class LusciousAPITH : Luscious("Luscious (API)", "https://api.luscious.net", "th")
class LusciousAPIALL : Luscious("Luscious (API)", "https://api.luscious.net", "all")
