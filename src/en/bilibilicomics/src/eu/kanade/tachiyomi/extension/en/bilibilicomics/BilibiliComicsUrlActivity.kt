package eu.kanade.tachiyomi.extension.en.bilibilicomics

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://www.bilibilicomics.com/detail/xxx intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 *
 * Main goal was to make it easier to open manga in Tachiyomi in spite of the DDoS blocking
 * the usual search screen from working.
 */
class BilibiliComicsUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val titleid = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${BilibiliComics.prefixIdSearch}${titleid.removePrefix("mc")}")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("BilibiliUrlActivity", e.toString())
            }
        } else {
            Log.e("BilibiliUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
