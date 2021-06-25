package eu.kanade.tachiyomi.extension.en.midnightmessscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

@Nsfw
class MidnightMessScans : Madara("Midnight Mess Scans", "https://midnightmess.org", "en") {
   
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        
        document.select("div.post-content").let {
           manga.description = it.select("div.manga-excerpt").text()
        }
        
        return manga     
    }
}
