package eu.kanade.tachiyomi.extension.ar.gmanga

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GmangaPreferences(id: Long) {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    fun setupPreferenceScreen(screen: PreferenceScreen) {

        STRING_PREFERENCES.forEach {
            val preference = ListPreference(screen.context).apply {
                key = it.key
                title = it.title
                entries = it.entries()
                entryValues = it.entryValues()
                summary = "%s"
            }

            if (!preferences.contains(it.key))
                preferences.edit().putString(it.key, it.default().key).apply()

            screen.addPreference(preference)
        }
    }

    fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {

        STRING_PREFERENCES.forEach {
            val preference = androidx.preference.ListPreference(screen.context).apply {
                key = it.key
                title = it.title
                entries = it.entries()
                entryValues = it.entryValues()
                summary = "%s"
            }

            if (!preferences.contains(it.key))
                preferences.edit().putString(it.key, it.default().key).apply()

            screen.addPreference(preference)
        }
    }

    fun getString(pref: StringPreference): String {
        return preferences.getString(pref.key, pref.default().key)!!
    }

    companion object {

        class StringPreferenceOption(val key: String, val title: String)

        class StringPreference(
            val key: String,
            val title: String,
            private val options: List<StringPreferenceOption>,
            private val defaultOptionIndex: Int = 0
        ) {
            fun entries(): Array<String> = options.map { it.title }.toTypedArray()
            fun entryValues(): Array<String> = options.map { it.key }.toTypedArray()
            fun default(): StringPreferenceOption = options[defaultOptionIndex]
        }

        // preferences
        const val PREF_CHAPTER_LISTING_SHOW_ALL = "gmanga_gmanga_chapter_listing_show_all"
        const val PREF_CHAPTER_LISTING_SHOW_POPULAR = "gmanga_chapter_listing_most_viewed"

        val PREF_CHAPTER_LISTING = StringPreference(
            "gmanga_chapter_listing",
            "كيفية عرض الفصل بقائمة الفصول",
            listOf(
                StringPreferenceOption(PREF_CHAPTER_LISTING_SHOW_POPULAR, "اختيار النسخة الأكثر مشاهدة"),
                StringPreferenceOption(PREF_CHAPTER_LISTING_SHOW_ALL, "عرض جميع النسخ")
            )
        )

        private val STRING_PREFERENCES = listOf(
            PREF_CHAPTER_LISTING
        )
    }
}
