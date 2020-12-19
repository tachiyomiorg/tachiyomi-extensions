package eu.kanade.tachiyomi.extension.en.bestmanhua

import eu.kanade.tachiyomi.lib.themesources.madara.Madara

class BestManhua : Madara("BestManhua", "https://bestmanhua.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}
