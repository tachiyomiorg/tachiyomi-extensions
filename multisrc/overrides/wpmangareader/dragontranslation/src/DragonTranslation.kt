/* ktlint-disable */
// THIS FILE IS AUTO-GENERATED; DO NOT EDIT
package eu.kanade.tachiyomi.extension.es.dragontranslation

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale


@Nsfw
class DragonTranslation : WPMangaReader("DragonTranslation", "https://dragontranslation.com", "es",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es")))
