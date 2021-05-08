package eu.kanade.tachiyomi.extension.ru.readmanga

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess
/**
 * Springboard that accepts https://readmanga.live/xxx intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 */
class ReadmangaActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 0) {
            val titleid = pathSegments[0]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${Readmanga.PREFIX_SLUG_SEARCH}$titleid")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ReadmangaActivity", e.toString())
            }
        } else {
            Log.e("ReadmangaActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
