package eu.kanade.tachiyomi.extension.pt.yaoitoshokan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class YaoiToshokan : Madara("Yaoi Toshokan", "https://yaoitoshokan.com.br", "pt-BR", SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))) {
    // Page has custom link to scan website.
    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    // Chapters are listed old to new.
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector)
            .mapIndexed { index, element ->
                // Had to add trim because of white space in source.
                val imageUrl = element.select("img").attr("data-src").trim()
                Page(index, document.location(), imageUrl)
            }
    }
}
