package eu.kanade.tachiyomi.extension.id.mangayosh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaYosh : Madara("MangaYosh", "https://mangayosh.xyz", "id", SimpleDateFormat("dd MMM yyyy", Locale.US))
