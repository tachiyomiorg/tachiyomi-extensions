package eu.kanade.tachiyomi.extension.fr.tsubakinoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.*

class TsubakiNoScan : Madara(
    "Tsubaki No Scan",
    "https://tsubakinoscan.com",
    "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
)
