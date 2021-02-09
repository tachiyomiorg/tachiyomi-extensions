package eu.kanade.tachiyomi.extension.id.pojokmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PojokManga : Madara("Pojok Manga", "https://pojokmanga.com", "id", SimpleDateFormat("MMM dd, yyyy", Locale.US))
