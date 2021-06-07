package eu.kanade.tachiyomi.extension.id.mangakita

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader

class MangaKita : WPMangaReader("MangaKita", "https://mangakita.net", "id") {
    override val hasProjectPage = true
}
