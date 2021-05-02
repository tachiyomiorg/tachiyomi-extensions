package eu.kanade.tachiyomi.extension.ar.queensmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class QueensManga : Madara("QueensManga ملكات المانجا", "https://queensmanga.com", "ar") {
    override fun chapterListSelector(): String = "div.listing-chapters_wrap a"
}
