package eu.kanade.tachiyomi.extension.pt.hikariscan

import eu.kanade.tachiyomi.multisrc.wpmangareader.WPMangaReader
import java.text.SimpleDateFormat
import java.util.Locale


class HikariScan : WPMangaReader("Hikari Scan", "https://hikariscan.com.br", "pt-BR", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("pt")))
