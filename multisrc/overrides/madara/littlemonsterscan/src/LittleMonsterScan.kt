package eu.kanade.tachiyomi.extension.pt.littlemonsterscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LittleMonsterScan : Madara("Little Monster Scan", "https://littlemonsterscan.com.br",
      "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))
