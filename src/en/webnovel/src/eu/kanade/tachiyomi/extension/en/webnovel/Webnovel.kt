package eu.kanade.tachiyomi.extension.en.webnovel

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource

import java.util.Date

import rx.Observable

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Webnovel : ParsedHttpSource() {
    override val name = "Webnovel"
    override val baseUrl = "https://www.webnovel.com"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    companion object {
        private const LOW_QUALITY = 150
        private const MED_QUALITY = 300
        private const HI_QUALITY = 600

        private const val PREFIX_ID_SEARCH = "id:"
    }

    private fun clientBuilder(r18Toggle: Int): OkHttpClient = network.cloudflareClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val originalCookies = chain.request().header("Cookie") ?: "" as String
                chain
            }.build()!!
}