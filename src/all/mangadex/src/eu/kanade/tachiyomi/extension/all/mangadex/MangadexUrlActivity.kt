package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Springboard that accepts https://mangadex.com/{title,manga}/xxx intents and redirects
 * them to the main tachiyomi process. The idea is to not install the intent filter
 * unless you have this extension installed, but still let the main tachiyomi app control
 * things.
 *
 * Main goal was to make it easier to open manga in Tachiyomi in spite of the DDoS blocking
 * the usual search screen from working.
 */
class MangadexUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val isChapter = (pathSegments[0] == "chapter")
            val titleid = pathSegments[1]
            val query = if (isChapter) { 
                "cid:$titleId"
            } else { 
                "id:$titleId"
            }
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                //putExtra("query", "id:$titleid")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangadexUrlActivity", e.toString())
            }
        } else {
            Log.e("MangadexUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        System.exit(0)
    }
}
