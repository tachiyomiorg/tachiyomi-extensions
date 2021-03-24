package eu.kanade.tachiyomi.extension.fr.scanmanga

import eu.kanade.tachiyomi.source.online.ParsedHttpSource

class ScanManga : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Scan-Manga"

    override val baseUrl = "https://www.scan-manga.com"

    override val lang = "fr"

    override val supportsLatest = true


}
