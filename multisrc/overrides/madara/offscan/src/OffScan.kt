package eu.kanade.tachiyomi.extension.pt.offscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class OffScan : Madara(
    "Off Scan",
    "https://offscan.top",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
)
