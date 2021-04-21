package eu.kanade.tachiyomi.extension.pt.winterscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class WinterScan : Madara("Winter Scan", "https://winterscan.com.br", "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))
