package eu.kanade.tachiyomi.extension.all.lusciousmembers

import eu.kanade.tachiyomi.multisrc.luscious.Luscious
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LusciousMembersFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LusciousMembersEN(),
        LusciousMembersJA(),
        LusciousMembersES(),
        LusciousMembersIT(),
        LusciousMembersDE(),
        LusciousMembersFR(),
        LusciousMembersZH(),
        LusciousMembersKO(),
        LusciousMembersOTHER(),
        LusciousMembersPT(),
        LusciousMembersTH(),
    )
}
class LusciousMembersEN : Luscious("Luscious (Members)", "https://members.luscious.net", "en")
class LusciousMembersJA : Luscious("Luscious (Members)", "https://members.luscious.net", "ja")
class LusciousMembersES : Luscious("Luscious (Members)", "https://members.luscious.net", "es")
class LusciousMembersIT : Luscious("Luscious (Members)", "https://members.luscious.net", "it")
class LusciousMembersDE : Luscious("Luscious (Members)", "https://members.luscious.net", "de")
class LusciousMembersFR : Luscious("Luscious (Members)", "https://members.luscious.net", "fr")
class LusciousMembersZH : Luscious("Luscious (Members)", "https://members.luscious.net", "zh")
class LusciousMembersKO : Luscious("Luscious (Members)", "https://members.luscious.net", "ko")
class LusciousMembersOTHER : Luscious("Luscious (Members)", "https://members.luscious.net", "other")
class LusciousMembersPT : Luscious("Luscious (Members)", "https://members.luscious.net", "pt")
class LusciousMembersTH : Luscious("Luscious (Members)", "https://members.luscious.net", "th")
