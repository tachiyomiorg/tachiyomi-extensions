package eu.kanade.tachiyomi.extension.fr.yokaijump

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class YokaiJump : Madara(
    "Yokai Jump",
    "https://yokaijump.fr",
    "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
)
