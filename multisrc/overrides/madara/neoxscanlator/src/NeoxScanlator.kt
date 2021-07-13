package eu.kanade.tachiyomi.extension.pt.neoxscanlator

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.Exception

class NeoxScanlator :
    Madara(
        "Neox Scanlator",
        DEFAULT_BASE_URL,
        "pt-BR",
        SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))
    ),
    ConfigurableSource {

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .addInterceptor(RateLimitInterceptor(1, 2, TimeUnit.SECONDS))
        .build()

    override val altNameSelector = ".post-content_item:contains(Alternativo) .summary-content"

    override val altName = "Nome alternativo: "

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl: String by lazy {
        preferences.getString(BASE_URL_PREF_KEY, DEFAULT_BASE_URL)!!
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", REFERER)

    // Filter the novels in pure text format.
    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override fun searchMangaParse(response: Response): MangasPage {
        val mangaPage = super.searchMangaParse(response)
        val filteredResult = mangaPage.mangas.filter { it.title.contains(NOVEL_REGEX).not() }

        return MangasPage(filteredResult, mangaPage.hasNextPage)
    }

    // Sometimes the site changes the manga URL. This override will
    // add an error instead of the HTTP 404 to inform the user to
    // migrate from Neox to Neox to update the URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservable()
            .doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception(if (response.code == 404) MIGRATION_MESSAGE else "HTTP error ${response.code}")
                }
            }
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // Only status and order by filter work.
    override fun getFilterList(): FilterList = FilterList(super.getFilterList().slice(3..4))

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_KEY
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(DEFAULT_BASE_URL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Padrão: $DEFAULT_BASE_URL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit()
                        .putString(BASE_URL_PREF_KEY, newValue as String)
                        .commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    companion object {
        private const val MIGRATION_MESSAGE = "O URL deste mangá mudou. " +
            "Faça a migração do Neox para o Neox para atualizar a URL."

        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/webp,image/apng,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val REFERER = "https://google.com/"

        private const val DEFAULT_BASE_URL = "https://neoxscans.com"
        private const val BASE_URL_PREF_KEY = "base_url_${BuildConfig.VERSION_NAME}"
        private const val BASE_URL_PREF_TITLE = "URL da fonte"
        private const val BASE_URL_PREF_SUMMARY = "Para uso temporário. Quando você atualizar a " +
            "extensão, esta configuração será apagada."

        private const val RESTART_TACHIYOMI = "Reinicie o Tachiyomi para aplicar as configurações."

        private val NOVEL_REGEX = "novel|livro".toRegex(RegexOption.IGNORE_CASE)
    }
}
