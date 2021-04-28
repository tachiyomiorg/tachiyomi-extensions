package eu.kanade.tachiyomi.extension.fr.gsnation

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class GSNation : WPMangaReader("GS Nation", "http://gs-nation.fr", "fr", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.FRANCE)) {

    // remove the novels from the response
    override fun popularMangaParse(response: Response): MangasPage {
        val mangasPage = super.popularMangaParse(response)

        return MangasPage(
            mangasPage.mangas
                .filterNot { it.title.startsWith("novel", true) }
                .distinctBy { it.url },
            mangasPage.hasNextPage
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select(".imptdt:contains(auteur) i").text()

        artist = document.select(".tsinfo .imptdt:contains(artiste) i").text()

        genre = document.select(".mgen a").joinToString { it.text() }
        status = parseStatus(document.select(".tsinfo .imptdt:contains(statut) i").text())

        thumbnail_url = document.select("div.thumb img").attr("abs:src")
        description = document.select(".entry-content[itemprop=description]").joinToString("\n") { it.text() }

        // add series type(manga/manhwa/manhua/other) thinggy to genre
        document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
            if (it.isEmpty().not() && genre!!.contains(it, true).not()) {
                genre += if (genre!!.isEmpty()) it else ", $it"
            }
        }

        // add alternative name to manga description
        document.select(altNameSelector).firstOrNull()?.ownText()?.let {
            if (it.isEmpty().not()) {
                description += when {
                    description!!.isEmpty() -> altName + it
                    else -> "\n\n$altName" + it
                }
            }
        }
    }

    override fun parseStatus(status: String) = when {
        status.contains("En cours") -> SManga.ONGOING
        status.contains("Terminée") -> SManga.COMPLETED
        status.contains("Licenciée") -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }
}
