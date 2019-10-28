package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


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


class ZH : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "zh", "cn")
class EN : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "en", "en")
class ID : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "id", "id")
class VI : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "vi", "vi")
class ES : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "es", "es")
class PT : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "pt", "pt")
class TH : MangaToon("MangaToon (Limited)", "https://mangatoon.mobi", "th", "th")


}
