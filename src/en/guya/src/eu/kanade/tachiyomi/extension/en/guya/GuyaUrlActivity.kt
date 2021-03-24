package eu.kanade.tachiyomi.extension.en.guya

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Accepts https://guya.moe/read/manga/xyz intents
 *
 * Added due to requests from various users to allow for opening of titles when given the
 * Guya URL whilst on mobile.
 */
class GuyaUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size >= 3) {
            Log.d("GuyaUrlActivity", pathSegments[0])

            val query = when (pathSegments[0]) {
                "proxy" -> {
                    val source = pathSegments[1]
                    val id = pathSegments[2]
                    "${Guya.PROXY_PREFIX}$source/$id"
                }
                else -> {
                    val slug = pathSegments[2]
                    "${Guya.SLUG_PREFIX}$slug"
                }
            }

            // Gotta do it like this since slug title != actual title
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("GuyaUrlActivity", e.toString())
            }
        } else {
            Log.e("GuyaUrlActivity", "Unable to parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
