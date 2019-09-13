package eu.kanade.tachiyomi.extension.all.mangadventure

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Springboard that accepts {baseUrl}/reader/{slug}
 * intents and redirects them to the main Tachiyomi process.
 */
class MangAdventureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.pathSegments?.takeIf { it.size > 1 }?.let {
            try {
                startActivity(Intent().apply {
                    action = "eu.kanade.tachiyomi.SEARCH"
                    putExtra("query", MangAdventure.SLUG_QUERY + it[1])
                    putExtra("filter", packageName)
                })
            } catch (ex: ActivityNotFoundException) {
                Log.e("MangAdventureActivity", ex.message, ex)
            }
        } ?: logInvalidIntent(intent)
        finish()
        exitProcess(0)
    }

    private fun logInvalidIntent(intent: Intent) {
        val msg = "Failed to parse URI from intent"
        Log.e("MangAdventureActivity",  "$msg $intent")
    }
}
