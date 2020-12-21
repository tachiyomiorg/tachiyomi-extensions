package eu.kanade.tachiyomi.extension.pt.hikariscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HikariScan : Madara("Hikari Scan", "https://hikariscan.com.br", "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))
