package eu.kanade.tachiyomi.extension.en.manhuafast

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaFast : Madara("ManhuaFast", "https://manhuafast.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}
