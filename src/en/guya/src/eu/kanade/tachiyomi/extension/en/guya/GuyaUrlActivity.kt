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
        val host = intent?.data?.host
        val pathSegments = intent?.data?.pathSegments

        if (host != null && pathSegments != null) {
            val query = when (host) {
                "m.imgur.com", "imgur.com" -> fromImgur(pathSegments)
                else -> fromGuya(pathSegments)
            }

            if (query == null) {
                Log.e("GuyaUrlActivity", "Unable to parse URI from intent $intent")
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
                Log.e("GuyaUrlActivity", e.toString())
            }
        }

        finish()
        exitProcess(0)
    }

    private fun fromImgur(pathSegments: List<String>): String? {
        if (pathSegments.size >= 2) {
            val id = pathSegments[1]

            return "${Guya.PROXY_PREFIX}imgur/$id"
        }
        return null
    }

    private fun fromGuya(pathSegments: MutableList<String>): String? {
        if (pathSegments.size >= 3) {
            return when (pathSegments[0]) {
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
        }
        return null
    }
}
