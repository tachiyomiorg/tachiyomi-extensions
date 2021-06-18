package eu.kanade.tachiyomi.extension.all.mangapark

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class MangaParkUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = fromGuya(pathSegments)

            if (query == null) {
                Log.e("MangaParkUrlActivity", "Unable to parse URI from intent $intent")
                finish()
                exitProcess(1)
            }

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("MangaParkUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun fromGuya(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 2) {
            val id = pathSegments[1]
            "id:$id"
        } else {
            null
        }
    }
}
