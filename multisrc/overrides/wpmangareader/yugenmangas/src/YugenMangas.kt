/* ktlint-disable */
// THIS FILE IS AUTO-GENERATED; DO NOT EDIT
package eu.kanade.tachiyomi.extension.es.yugenmangas

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import java.text.SimpleDateFormat
import java.util.Locale




class YugenMangas : WPMangaReader("YugenMangas", "https://yugenmangas.com", "es",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es")))
