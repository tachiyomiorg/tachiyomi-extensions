package eu.kanade.tachiyomi.lib.urlhandler

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

abstract class UrlHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val query = getQueryFromPathSegments(pathSegments)
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", query)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(localClassName, e.toString())
            }
        } else {
            Log.e(localClassName, "Could not parse uri from intent: $intent")
        }

        finish()
        System.exit(0)
    }

    abstract fun getQueryFromPathSegments(pathSegments: List<String>): String

}
