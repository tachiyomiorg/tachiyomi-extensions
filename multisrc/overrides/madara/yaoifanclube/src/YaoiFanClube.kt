package eu.kanade.tachiyomi.extension.pt.yaoifanclube

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class YaoiFanClube : Madara(
    "Yaoi Fan Clube",
    "https://yaoifanclube.com.br",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR"))
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    // [...document.querySelectorAll('input[name="genre[]"]')]
    //   .map(x => `Genre("${document.querySelector('label[for=' + x.id + ']').innerHTML.trim()}", "${x.value}")`)
    //   .join(',\n')
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Adulto", "adulto"),
        Genre("Aeronáutica", "aeronautica"),
        Genre("BNHA", "bnha"),
        Genre("Coletânea", "coletanea"),
        Genre("Comédia", "comedia"),
        Genre("Crossdress", "crossdress"),
        Genre("Drama", "drama"),
        Genre("Esporte", "esporte"),
        Genre("Fantasia", "fantasia"),
        Genre("Fetiche", "fetiche"),
        Genre("Ficção", "ficcao"),
        Genre("Histórico", "historico"),
        Genre("KNB", "knb"),
        Genre("Mistério", "misterio"),
        Genre("Música", "musica"),
        Genre("Omegaverse", "omegaverse"),
        Genre("Paródia", "parodia"),
        Genre("Patinação", "patinacao"),
        Genre("Policial", "policial"),
        Genre("Psicológico", "psicologico"),
        Genre("Robô", "robo"),
        Genre("Romance", "romance"),
        Genre("Shounen-ai", "shounen-ai"),
        Genre("Smut", "smut"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Terror", "terror"),
        Genre("Tragédia", "tragedia"),
        Genre("Vida Cotidiana", "vida-cotidiana"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Yaoi", "yaoi"),
        Genre("Zumbi", "zumbi")
    )
}
