package eu.kanade.tachiyomi.extension.pt.yuriverso

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.*

class YuriVerso : Madara(
    "Yuri Verso",
    "https://yuri.live",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
)
