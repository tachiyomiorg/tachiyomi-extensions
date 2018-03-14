package eu.kanade.tachiyomi.extension.all.foolslide

import android.util.Base64
import com.github.salomonbrys.kotson.get
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

/**
 * Created by Carlos on 3/14/2018.
 */
class FoolSlideFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllFoolSlide()
}


fun getAllFoolSlide(): List<Source> {
    return listOf(JaminisBox(), ChampionScans())
}

class JaminisBox : FoolSlide("Jaimini's Box", "https://jaiminisbox.com", "en", "/reader") {

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val base64Json = doc.substringAfter("JSON.parse(atob(\"").substringBefore("\"));")
        val decodeJson = String(Base64.decode(base64Json, Base64.DEFAULT))
        val json = JsonParser().parse(decodeJson).asJsonArray
        val pages = mutableListOf<Page>()
        json.forEach {
            pages.add(Page(pages.size, "", it.get("url").asString))
        }
        return pages
    }
}

class ChampionScans : FoolSlide("Champion Scans", "http://reader.championscans.com", "en")
