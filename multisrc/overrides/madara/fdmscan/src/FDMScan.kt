package eu.kanade.tachiyomi.extension.pt.fdmscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class FDMScan : Madara("FDM Scan", "https://fdmscan.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")))
