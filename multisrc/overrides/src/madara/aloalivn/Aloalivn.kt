package eu.kanade.tachiyomi.extension.en.aloalivn

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Aloalivn : Madara("Aloalivn", "https://aloalivn.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}
