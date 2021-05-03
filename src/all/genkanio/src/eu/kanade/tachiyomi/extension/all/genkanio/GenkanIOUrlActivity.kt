package eu.kanade.tachiyomi.extension.all.genkanio

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class GenkanIOUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size >= 2) {
            // url scheme is of the form "/manga/ID-MANGANAME"
            val (_, titleComponent) = pathSegments

            // This is essentially substringBefore(titleComponent, '-'), don't have access to stdlib
            var titleId = ""
            for (i in 0 until titleComponent.length) {
                if (titleComponent[i] == '-') break
                titleId = titleId.plus(titleComponent[i])
            }

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", titleId)
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("GenkanIOUrlActivity", e.toString())
            }
        } else {
            Log.e("GenkanIOUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
