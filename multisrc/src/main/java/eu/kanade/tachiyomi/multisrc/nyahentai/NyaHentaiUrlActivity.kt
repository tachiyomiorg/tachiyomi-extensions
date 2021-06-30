package eu.kanade.tachiyomi.multisrc.nyahentai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import kotlin.system.exitProcess

/**
 * Springboard that accepts https://www.manhuagui.com/comic/xxx intents and redirects them to
 * the main tachiyomi process. The idea is to not install the intent filter unless
 * you have this extension installed, but still let the main tachiyomi app control
 * things.
 */
class NyaHentaiUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val titleid = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${NyaHentai.PREFIX_ID_SEARCH}$titleid")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("NyaHentaiUrlActivity", e.toString())
            }
        } else {
            Log.e("NyaHentaiUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }
}
