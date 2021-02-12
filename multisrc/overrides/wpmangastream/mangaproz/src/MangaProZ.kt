package eu.kanade.tachiyomi.extension.ar.mangaproz

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class MangaProZ : WPMangaStream("Manga Pro Z", "https://mangaproz.com", "ar") {
    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply { name = name.removeSuffix(" free") }
}
