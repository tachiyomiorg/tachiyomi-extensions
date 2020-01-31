package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaToonFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ZH(),
        EN(),
        ID(),
        VI(),
        ES(),
        PT(),
        TH()
    )

class ZH : MangaToon("zh", "cn")
class EN : MangaToon("en", "en")
class ID : MangaToon("id", "id")
class VI : MangaToon("vi", "vi")
class ES : MangaToon("es", "es")
class PT : MangaToon("pt", "pt")
class TH : MangaToon("th", "th")
}
