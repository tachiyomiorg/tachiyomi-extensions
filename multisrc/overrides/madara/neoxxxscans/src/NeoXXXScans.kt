package eu.kanade.tachiyomi.extension.pt.neoxxxscans

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Nsfw
class NeoXXXScans :
    Madara(
        "NeoXXX Scans",
        DEFAULT_BASE_URL,
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    ),
    ConfigurableSource {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 2, TimeUnit.SECONDS))
        .build()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl: String by lazy {
        preferences.getString(BASE_URL_PREF_KEY, DEFAULT_BASE_URL)!!
    }

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
        private const val DEFAULT_BASE_URL = "https://xxx.neoxscans.com"
        private const val BASE_URL_PREF_KEY = "base_url_${BuildConfig.VERSION_NAME}"
        private const val BASE_URL_PREF_TITLE = "URL da fonte"
        private const val BASE_URL_PREF_SUMMARY = "Para uso temporário. Quando você atualizar a " +
            "extensão, esta configuração será apagada."

        private const val RESTART_TACHIYOMI = "Reinicie o Tachiyomi para aplicar as configurações."
    }
}
