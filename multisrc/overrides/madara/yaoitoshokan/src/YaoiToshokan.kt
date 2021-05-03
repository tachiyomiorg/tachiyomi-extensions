package eu.kanade.tachiyomi.extension.pt.yaoitoshokan

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class YaoiToshokan : Madara(
    "Yaoi Toshokan",
    "https://yaoitoshokan.net",
    "pt-BR",
    SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    // Page has custom link to scan website.
    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector)
            .mapIndexed { index, element ->
                // Had to add trim because of white space in source.
                val imageUrl = element.select("img").attr("data-src").trim()
                Page(index, document.location(), imageUrl)
            }
    }

    // [...document.querySelectorAll('input[name="genre[]"]')]
    //   .map(x => `Genre("${document.querySelector('label[for=' + x.id + ']').innerHTML.trim()}", "${x.value}")`)
    //   .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Adulto", "adulto"),
        Genre("Bara", "bara"),
        Genre("BDSM", "bdsm"),
        Genre("Comédia", "comedia"),
        Genre("Comic", "comic"),
        Genre("Cotidiano", "cotidiano"),
        Genre("Crossdress", "gender-bender"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Esportes", "esportes"),
        Genre("Fantasia", "fantasia"),
        Genre("Fury", "fury"),
        Genre("Futanari", "futanari"),
        Genre("Gender Bender", "gender-bender-2"),
        Genre("Histórico", "historico"),
        Genre("Horror", "horror"),
        Genre("Incesto", "incesto"),
        Genre("Mafia", "mafia"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Mistério", "misterio"),
        Genre("Mpreg", "mpreg"),
        Genre("Omegaverse", "omegaverse"),
        Genre("One shot", "one-shot"),
        Genre("Poliamor", "poliamor"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("Salaryman", "salaryman"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shocaton", "shocaton"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Smut", "smut"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Tragédia", "tragedia"),
        Genre("Vampiros", "vampiros"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Yaoi", "yaoi")
    )
}
