package eu.kanade.tachiyomi.extension.en.mangazukionline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient

class MangazukiOnline : Madara("Mangazuki.online", "http://mangazukinew.online", "en") {
    override val client: OkHttpClient = super.client.newBuilder().followRedirects(true).build()
}
