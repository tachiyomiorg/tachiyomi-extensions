package eu.kanade.tachiyomi.multisrc.wpmangareader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import kotlin.system.exitProcess

class WPMangaReaderUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size >= 2) {

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query","${WPMangaReader.URL_SEARCH_PREFIX}${intent?.data?.toString()}")
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("WPMangaReaderUrl", e.toString())
            }
        } else {
            Log.e("WPMangaReaderUrl", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
