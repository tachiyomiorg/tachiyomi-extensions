package eu.kanade.tachiyomi.extension.ja.rawmangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class RawMangas : Madara("Raw Mangas", "https://rawmangas.net", "ja", SimpleDateFormat("MMMM dd, yyyy", Locale.US))
