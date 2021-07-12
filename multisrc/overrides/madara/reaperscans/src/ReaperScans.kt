package eu.kanade.tachiyomi.extension.en.reaperscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ReaperScans : Madara("Reaper Scans", "https://reaperscans.com", "en") {
    override val versionId = 2

    override fun popularMangaSelector() = "div.page-item-detail.manga"
}
