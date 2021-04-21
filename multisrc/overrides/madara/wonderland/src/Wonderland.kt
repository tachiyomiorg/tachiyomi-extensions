package eu.kanade.tachiyomi.extension.pt.wonderland

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Wonderland : Madara("Wonderland", "https://landwebtoons.site/", 
      "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))