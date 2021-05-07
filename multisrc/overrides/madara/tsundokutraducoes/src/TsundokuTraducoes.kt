package eu.kanade.tachiyomi.extension.pt.tsundokutraducoes

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TsundokuTraducoes : Madara(
    "Tsundoku Traduções",
    "https://tsundokutraducoes.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
) {

    // Hardcode the id because the language code was wrong.
    override val id: Long = 3941383635597527601

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun popularMangaSelector() = "div.page-item-detail.manga"

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
        Genre("Fantasia", "fantasia"),
        Genre("Feminismo", "feminismo"),
        Genre("Gore", "gore"),
        Genre("Guerra", "guerra"),
        Genre("Harém", "harem"),
        Genre("Hentai", "hentai"),
        Genre("Horror", "horror"),
        Genre("Humor Negro", "humor-negro"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Joshikousei", "joshikousei"),
        Genre("Maduro", "maduro"),
        Genre("Mistério", "misterio"),
        Genre("Otaku", "otaku"),
        Genre("Psicológico", "psicologico"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Romance", "romance"),
        Genre("RPG", "rpg"),
        Genre("Sátira", "satira"),
        Genre("Seinen", "seinen"),
        Genre("Sexo Explícito", "sexo-explicito"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice-of-Life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Terror", "terror"),
        Genre("Tragédia", "tragedia"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Xianxia", "xianxia"),
        Genre("Yuri", "yuri")
    )
}
