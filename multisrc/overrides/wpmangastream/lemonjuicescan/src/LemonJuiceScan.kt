package eu.kanade.tachiyomi.extension.pt.lemonjuicescan

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class LemonJuiceScan : WPMangaStream(
    "Lemon Juice Scan",
    "https://lemonjuicescan.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    // [...document.querySelectorAll('ul.taxindex li a')]
    //     .map(x => `Genre("${x.querySelector("span").innerHTML}", "${x.href.replace(/.*\/genres\/(.*)\//, '$1')}")`)
    //     .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("ABO", "abo"),
        Genre("Abuso físico", "abuso-fisico"),
        Genre("Ação", "acao"),
        Genre("Agressão", "agressao"),
        Genre("Amigos de Infância", "amigos-de-infancia"),
        Genre("Anjos", "anjos"),
        Genre("Aventura", "aventura"),
        Genre("Bara", "bara"),
        Genre("BDSM", "bdsm"),
        Genre("Bestas", "bestas"),
        Genre("BL", "bl"),
        Genre("BL Romance", "bl-romance"),
        Genre("BLNacional", "blnacional"),
        Genre("BlRomance", "blromance"),
        Genre("Boys Love", "boys-love"),
        Genre("BoysLove", "boyslove"),
        Genre("Bullying", "bullying"),
        Genre("Cárcere", "carcere"),
        Genre("Cárcere Privado", "carcere-privado"),
        Genre("China antiga", "china-antiga"),
        Genre("Colegial", "colegial"),
        Genre("Comédia", "comedia"),
        Genre("Comic", "comic"),
        Genre("Comic BR", "comic-br"),
        Genre("Criminal", "criminal"),
        Genre("Crossdressing", "crossdressing"),
        Genre("Demônios", "demonios"),
        Genre("Drama", "drama"),
        Genre("Estupro", "estupro"),
        Genre("Família", "familia"),
        Genre("Fantasia", "fantasia"),
        Genre("Fetiche", "fetiche"),
        Genre("Ficção Científica", "ficcao-cientifica"),
        Genre("Folclore", "folclore"),
        Genre("Furry", "furry"),
        Genre("Gatilhos", "gatilhos"),
        Genre("Gêmeos", "gemeos"),
        Genre("GenderBend", "genderbend"),
        Genre("Hard", "hard"),
        Genre("Histórico", "historico"),
        Genre("Incesto", "incesto"),
        Genre("Isekai", "isekai"),
        Genre("Magia", "magia"),
        Genre("Mangá", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Manpreg", "manpreg"),
        Genre("Manpregnant", "manpregnant"),
        Genre("Mistério", "misterio"),
        Genre("Mitológico", "mitologico"),
        Genre("Mortes", "mortes"),
        Genre("Mpreg", "mpreg"),
        Genre("Mpregnant", "mpregnant"),
        Genre("Nacional", "nacional"),
        Genre("Novel", "novel"),
        Genre("Obscenidade", "obscenidade"),
        Genre("Omegaverse", "omegaverse"),
        Genre("One-shot", "one-shot"),
        Genre("Psicológico", "psicologico"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Religioso", "religioso"),
        Genre("Romance", "romance"),
        Genre("Romance Histórico", "romance-historico"),
        Genre("Sexo", "sexo"),
        Genre("Shonen-ai", "shonen-ai"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("SliceOfLife", "sliceoflife"),
        Genre("Smut", "smut"),
        Genre("Sobrenarutal", "sobrenarutal"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Soft", "soft"),
        Genre("Suicídio", "suicidio"),
        Genre("Suspense", "suspense"),
        Genre("Tortura", "tortura"),
        Genre("Trauma", "trauma"),
        Genre("Triângulo amoroso", "triangulo-amoroso"),
        Genre("Trisal", "trisal"),
        Genre("Vampiro", "vampiro"),
        Genre("Viagem No Tempo", "viagem-no-tempo"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Vingança", "vinganca"),
        Genre("Violência", "violencia"),
        Genre("Web Comic", "web-comic"),
        Genre("Webcomic", "webcomic"),
        Genre("Webtoon", "webtoon"),
        Genre("Yaoi", "yaoi")
    )
}
