package eu.kanade.tachiyomi.extension.fr.fukushuunoyuusha

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class FukushuuNoYuusha : Madara(
    "Fukushuu no Yuusha", "https://fny-scantrad.com/", 
    "fr", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))