package eu.kanade.tachiyomi.extension.en.manhuaes

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaES : Madara("Manhua ES", "https://manhuaes.com", "en") {
    override val pageListParseSelector = ".reading-content div.text-left :has(>img)"
}
