package eu.kanade.tachiyomi.extension.en.mangagreat

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaGreat : Madara("MangaGreat", "https://mangagreat.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}
