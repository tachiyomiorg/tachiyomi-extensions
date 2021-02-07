package eu.kanade.tachiyomi.extension.fr.atmsubs

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ATMSubs : Madara("ATM-Subs", "https://atm-subs.fr", "fr", SimpleDateFormat("d MMMM yyyy", Locale("fr")))
