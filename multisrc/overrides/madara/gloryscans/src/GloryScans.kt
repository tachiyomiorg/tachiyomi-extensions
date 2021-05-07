package eu.kanade.tachiyomi.extension.pt.gloryscans

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class GloryScans : Madara(
    "Glory Scans",
    "https://gloryscan.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    // [...document.querySelectorAll('input[name="genre[]"]')]
    //   .map(x => `Genre("${document.querySelector('label[for=' + x.id + ']').innerHTML.trim()}", "${x.value}")`)
    //   .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Adulto", "adulto"),
        Genre("Artes Marciais", "artes-marciais"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Esporte", "esporte"),
        Genre("Fantasia", "fantasia"),
        Genre("Harém", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Horror", "horror"),
        Genre("Horror", "horror-horror"),
        Genre("Isekai", "isekai"),
        Genre("Magia", "magia"),
        Genre("Mistério", "misterio"),
        Genre("Monstros", "monstros"),
        Genre("Psicologico", "psicologico"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Romance", "romance"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Sobrevivência", "sobrevivencia"),
        Genre("superpoderes", "superpoderes"),
        Genre("Suspense", "suspense"),
        Genre("Tragédia", "tragedia"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Vingança", "vinganca"),
        Genre("Webtoon", "webtoon"),
        Genre("Yuri", "yuri")
    )
}
