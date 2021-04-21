package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArgosScan : Madara("Wonderland Land Webtoons", "https://landwebtoons.site/", "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))
