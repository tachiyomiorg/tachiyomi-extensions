package eu.kanade.tachiyomi.extension.pt.blmanhwaclub

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class BlManhwaClub : Madara(
    "BL Manhwa Club",
    "https://blmanhwa.club",
    "pt-BR",
    SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    // [...document.querySelectorAll('div.genres li a')]
    //     .map(x => `Genre("${x.innerText.slice(1, -4).replace('(', '').trim()}", "${x.href.replace(/.*genero\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Ação", "acao"),
        Genre("Adulto", "adulto"),
        Genre("Aventura", "aventura"),
        Genre("Comédia", "comedia"),
        Genre("Cotidiano", "cotidiano"),
        Genre("Drama", "drama"),
        Genre("Esporte", "esporte"),
        Genre("Fantasia", "fantasia"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Histórico", "historico"),
        Genre("Horror", "horror"),
        Genre("Mafia", "mafia"),
        Genre("Mistério", "misterio"),
        Genre("Omegaverse", "omegaverse"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Super Herói", "super-heroi"),
        Genre("Tragédia", "tragedia"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Yaoi", "yaoi")
    )
}
