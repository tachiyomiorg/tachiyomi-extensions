package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class RandomScan : Madara("RandomScan", "https://randomscan.online/", "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))
