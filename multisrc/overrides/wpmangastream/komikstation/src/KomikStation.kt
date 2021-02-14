package eu.kanade.tachiyomi.extension.id.komikstation

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import com.github.salomonbrys.kotson.get
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.source.ConfigurableSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import rx.Observable
import java.net.URLEncoder

class KomikStation : WPMangaStream("Komik Station", "https://komikstation.com", "id") {
    // Formerly "Komik Station (WP Manga Stream)"
    override val id = 6148605743576635261
}
