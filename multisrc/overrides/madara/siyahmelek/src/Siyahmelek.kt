package eu.kanade.tachiyomi.extension.tr.siyahmelek

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.annotations.Nsfw
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Siyahmelek : Madara("Siyahmelek", "https://siyahmelek.com", "tr", SimpleDateFormat("dd MMM yyyy", Locale("tr")))
