package eu.kanade.tachiyomi.extension.all.foolslidecustomizable

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.source.ConfigurableSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FoolSlideCustomizableFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        FoolSlideCustomizable(),
    )
}
class FoolSlideCustomizable : ConfigurableSource, FoolSlide("FoolSlide Customizable", "", "other") {
    override val baseUrl: String by lazy { getPrefBaseUrl() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
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

    /**
     *  Tell the user to include /directory/ in the URL even though we remove it
     *  To increase the chance they input a usable URL
     */
    private fun getPrefBaseUrl() = preferences.getString(BASE_URL_PREF, DEFAULT_BASEURL)!!.substringBefore("/directory")

    companion object {
        private const val DEFAULT_BASEURL = "https://127.0.0.1"
        private const val BASE_URL_PREF_TITLE = "Example URL: https://domain.com/path_to/directory/"
        private const val BASE_URL_PREF = "overrideBaseUrl_v${BuildConfig.VERSION_NAME}"
        private const val BASE_URL_PREF_SUMMARY = "Connect to a designated FoolSlide server"
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
    }
}
