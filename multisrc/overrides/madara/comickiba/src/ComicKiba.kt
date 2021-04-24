package eu.kanade.tachiyomi.extension.en.comickiba

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComicKiba : Madara("ComicKiba", "https://comickiba.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item img:nth-child(1), div.reading-content p > img, .read-container .reading-content img"
}
