package eu.kanade.tachiyomi.extension.ko.mangashowme

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

class ManaMoaUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 1) {
            val titleid = pathSegments[1].substringBetween("manga_id=", "&")
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", "${ManaMoa.PREFIX_ID_SEARCH}$titleid")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e("ManaMoaUrlActivity", e.toString())
            }
        } else {
            Log.e("ManaMoaUrlActivity", "could not parse uri from intent $intent")
        }

        finish()
        exitProcess(0)
    }

}
