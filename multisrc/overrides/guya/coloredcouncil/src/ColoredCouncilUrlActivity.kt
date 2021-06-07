package eu.kanade.tachiyomi.extension.en.coloredcouncil

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Accepts https://danke.moe/read/manga/xyz intents
 *
 * Added due to requests from various users to allow for opening of titles when given the
 * Guya URL whilst on mobile.
 */
class ColoredCouncilUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = fromColoredCouncil(pathSegments)

            if (query == null) {
                Log.e("ColoredCouncilUrlActivity", "Unable to parse URI from intent $intent")
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
                Log.e("ColoredCouncilUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun fromColoredCouncil(pathSegments: MutableList<String>): String? {
        return if (pathSegments.size >= 3) {
            val slug = pathSegments[2]
            "${ColoredCouncil.SLUG_PREFIX}$slug"
        } else {
            null
        }
    }
}
