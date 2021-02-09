package eu.kanade.tachiyomi.extension.ja.rawmangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RawMangas : Madara("Raw Mangas", "https://rawmangas.net", "ja", SimpleDateFormat("MMMM dd, yyyy", Locale.US))
