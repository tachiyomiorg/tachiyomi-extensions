package eu.kanade.tachiyomi.extension.en.mangaplus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Springboard that accepts https://mangaplus.shueisha.co.jp/titles/xxx intents and redirects
 * them to the main tachiyomi process. The idea is to not install the intent filter
 * unless you have this extension installed, but still let the main tachiyomi app control
 * things.
 *
 * Main goal was to make it easier to open manga in Tachiyomi, as preview pages only
 * have the title "MANGA Plus by SHUEISHA".
 */
class MangaPlusUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val isViewer = (pathSegments[0] == "viewer")
            val titleid = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", isViewer ? "cid:$titleId" : "id:$titleid")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangaPlusUrlActivity", e.toString())
            }
        } else {
            Log.e("MangaPlusUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        System.exit(0)
    }
}