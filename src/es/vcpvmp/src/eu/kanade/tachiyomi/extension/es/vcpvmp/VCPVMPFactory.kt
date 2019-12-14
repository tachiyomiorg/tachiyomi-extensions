package eu.kanade.tachiyomi.extension.es.vcpvmp

import okhttp3.Request
import okhttp3.HttpUrl
import eu.kanade.tachiyomi.source.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*

class VCPVMPFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        VCP(),
        VMP()
    )
}

class VCP : VCPVMP("VCP", "https://vercomicsporno.com")

class VMP : VCPVMP("VMP", "https://vermangasporno.com") {

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/pagina/$page", headers)

    //override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/pagina/$page?s=$query", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = HttpUrl.parse(baseUrl)!!.newBuilder()

        url.addPathSegments("pagina")
        url.addPathSegments(page.toString())
        url.addQueryParameter("s", query)

        filters.forEach { filter ->
            when (filter) {
                is Genre -> {
                    when (filter.toUriPart().isNotEmpty()) {
                        true -> {
                            url = HttpUrl.parse(baseUrl)!!.newBuilder()

                            url.addPathSegments("genero")
                            url.addPathSegments(filter.toUriPart())

                            url.addPathSegments("pagina")
                            url.addPathSegments(page.toString())
                        }
                    }
                }
                is MangaList -> {
                    filter.state
                        .filter { manga -> manga.state }
                        .forEach {
                            manga -> url.addQueryParameter("cat", manga.id)
                        }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    private class Manga(name: String, val id: String) : Filter.CheckBox(name)

    private class MangaList(genres: List<Manga>) : Filter.Group<Manga>("Filtrar por manga", genres)

    override fun getFilterList() = FilterList(
        Genre(),
        Filter.Separator(),
        MangaList(getMangasList())
    )

    // Array.from(document.querySelectorAll('div.tagcloud a.tag-cloud-link')).map(a => `Pair("${a.innerText}", "${a.href.replace('https://vermangasporno.com/genero/', '')}")`).join(',\n')
    // from https://vermangasporno.com/
    private class Genre : UriPartFilter("Generos", arrayOf(
        Pair("Ver todos", ""),
        Pair("Ahegao", "ahegao"),
        Pair("Anal", "anal"),
        Pair("Big Ass", "big-ass"),
        Pair("Big Breasts", "big-breasts"),
        Pair("Blowjob", "blowjob"),
        Pair("Cheating", "cheating"),
        Pair("Fullcolor", "fullcolor"),
        Pair("Group", "group"),
        Pair("Incest", "incest"),
        Pair("loli", "loli"),
        Pair("Lolicon", "lolicon"),
        Pair("Milf", "milf"),
        Pair("Nakadashi", "nakadashi"),
        Pair("Netorare", "netorare"),
        Pair("Paizuri", "paizuri"),
        Pair("Schoolgirl Uniform", "schoolgirl-uniform"),
        Pair("Sole Female", "sole-female"),
        Pair("Stockings", "stockings"),
        Pair("Tetona", "tetona"),
        Pair("Tetonas", "tetonas")
    ))

    // Array.from(document.querySelectorAll('form select#cat option.level-0')).map(a => `Genre("${a.innerText}", "${a.value}")`).join(',\n')
    // from https://vermangasporno.com/
    private fun getMangasList() = listOf(
        Manga("3×3 Eyes", "1325"),
        Manga("Accel World", "175"),
        Manga("Aikatsu!", "1983"),
        Manga("Amagami", "1194"),
        Manga("Amagi Brilliant Park", "209"),
        Manga("Amano Megumi ha Sukidarake!", "888"),
        Manga("Ane Doki", "1121"),
        Manga("Angel Beats!", "176"),
        Manga("Ano Hi Mita Hana no Namae wo Bokutachi wa Mada Shiranai", "1097"),
        Manga("Another", "177"),
        Manga("Ansatsu Kyoushitsu", "2"),
        Manga("Aoi Hana", "1256"),
        Manga("Aquarion EVOL", "2089"),
        Manga("Arcana Heart", "934"),
        Manga("Arslan senki", "2068"),
        Manga("Asobi ni Iku yo!", "1280"),
        Manga("Azur Lane", "2076"),
        Manga("Baka to Test to Shoukanjuu", "1202"),
        Manga("Baka to Test to Shoukanjuu | Autor: Kurosawa Kiyotaka", "1201"),
        Manga("Bakemonogatari", "931"),
        Manga("Bakuman", "1259"),
        Manga("BanG Dream!", "1863"),
        Manga("Batman", "184"),
        Manga("bijin onna joushi takizawa-san", "2104"),
        Manga("Bishoujo Senshi Sailor Moon", "745"),
        Manga("Bleach", "82"),
        Manga("Blend S", "2102"),
        Manga("Blood+", "189"),
        Manga("Boku no Hero Academia", "245"),
        Manga("Boku wa tomodachi ga sukunai", "674"),
        Manga("Boruto", "2071"),
        Manga("Capcom", "251"),
        Manga("Charlotte", "1444"),
        Manga("Clannad", "102"),
        Manga("Claymore", "170"),
        Manga("Code Geass", "171"),
        Manga("Cyberbots", "981"),
        Manga("Dagashi Kashi", "733"),
        Manga("Danganronpa", "92"),
        Manga("Danmachi", "1042"),
        Manga("Darker than Black", "995"),
        Manga("Darker than Black: The Black Contractor", "1086"),
        Manga("Darkstalkers", "1996"),
        Manga("Date A Live", "670"),
        Manga("Dead Or Alive", "233"),
        Manga("Deadman Wonderland", "900"),
        Manga("Denpa Onna to Seishun Otoko", "952"),
        Manga("Dokidoki! Precure", "1919"),
        Manga("Dr. Slump", "977"),
        Manga("Dragon Quest", "3"),
        Manga("dragon quest iii", "239"),
        Manga("Dragonball", "4"),
        Manga("Dragon’s Crown", "1064"),
        Manga("DREAM C CLUB", "941"),
        Manga("Dungeon Ni Deai O Motomeru No Wa Machigatteiru Darou Ka", "226"),
        Manga("Dungeon ni Deai wo Motomeru no wa Machigatteiru Darou ka", "127"),
        Manga("Dungeon Travelers", "1951"),
        Manga("Dynasty Warriors", "1885"),
        Manga("Eromanga Sensei", "901"),
        Manga("Evangelion", "172"),
        Manga("Fairy Tail", "348"),
        Manga("Fantasy Earth Zero", "1433"),
        Manga("Fate Kaleid Liner Prisma Illya", "237"),
        Manga("Fate Stay Night", "235"),
        Manga("Fate/Grand Order", "891"),
        Manga("Fate/hollow ataraxia", "1294"),
        Manga("Fate/stay night", "722"),
        Manga("Final Fantasy", "591"),
        Manga("Final Figh", "1215"),
        Manga("Freezing", "190"),
        Manga("Full Metal Daemon: Muramasa", "122"),
        Manga("Full Metal Panic", "167"),
        Manga("Fullmetal Alchemist", "118"),
        Manga("Furry", "1972"),
        Manga("Gabriel Dropout", "884"),
        Manga("Gakkou Gurashi!", "1047"),
        Manga("Gate: Jieitai Kano Chi nite Kaku Tatakaeri", "761"),
        Manga("Gate: Jieitai Kanochi nite", "241"),
        Manga("Gate: Jieitai Kanochi nite Kaku Tatakaeri", "243"),
        Manga("Gegege no Kitarou", "1862"),
        Manga("Getsuyoubi no Tawawa", "250"),
        Manga("Ghost In The Shell", "1898"),
        Manga("Girls und Panzer", "904"),
        Manga("Gochuumon wa Usagi Desu ka?", "5"),
        Manga("Granblue Fantasy", "257"),
        Manga("Grisaia no Kajitsu", "6"),
        Manga("Guilty Gear", "727"),
        Manga("Gundam 00", "857"),
        Manga("Gundam Build Fighters", "7"),
        Manga("Gundam SEED Destiny", "103"),
        Manga("Hanasaku Iroha", "1104"),
        Manga("Hanayamata", "1947"),
        Manga("Hatsukoi Delusion", "1930"),
        Manga("Hayate no Gotoku!", "595"),
        Manga("He Is My Master", "104"),
        Manga("Hentai Ouji to Warawanai Neko.", "8"),
        Manga("Hibike! Euphonium", "9"),
        Manga("Highschool Dead", "168"),
        Manga("Highschool DxD", "105"),
        Manga("Highschool of the Dead", "937"),
        Manga("Himouto! Umaru-chan", "1039"),
        Manga("Hokenshitsu no Shinigami", "1105"),
        Manga("Hyperdimension Neptunia", "1567"),
        Manga("Ichigo 100%", "151"),
        Manga("Incesto", "182"),
        Manga("Infinite Stratos", "786"),
        Manga("K-ON", "150"),
        Manga("Kaichou wa Maid-sama!", "1129"),
        Manga("Kaiten Mutenmaru", "911"),
        Manga("Kami Nomi zo Shiru Sekai", "1091"),
        Manga("Kämpfer", "1120"),
        Manga("Kangoku Gakuen", "1036"),
        Manga("Kanon", "65"),
        Manga("Kantai Collection: KanColle", "10"),
        Manga("Kara no Kyoukai", "731"),
        Manga("Kenichi", "96"),
        Manga("Keroro Gunsou", "11"),
        Manga("Kidou Senshi Gundam 00", "960"),
        Manga("Kill la Kill", "1851"),
        Manga("Kimi ni Todoke", "1127"),
        Manga("KimiKiss", "1182"),
        Manga("King of fighter", "155"),
        Manga("King of Fighters", "788"),
        Manga("Kobayashi-san-chi no Maid Dragon", "299"),
        Manga("Koihime Musou", "1188"),
        Manga("Kono Subarashii Sekai Ni Syukufuku O", "259"),
        Manga("Kono Subarashii Sekai ni Syukufuku o!", "853"),
        Manga("Kurogane no Linebarrels", "1150"),
        Manga("Kyoukai Senjou no Horizon", "660"),
        Manga("Ladies Versus Butlers", "204"),
        Manga("Love Hina", "94"),
        Manga("Love Live Sunshine", "207"),
        Manga("Love Live!", "716"),
        Manga("Love Live! School Idol Project", "12"),
        Manga("Love Plus", "1013"),
        Manga("Lucky Star", "893"),
        Manga("Macross Frontier", "157"),
        Manga("Mahou Sensei Negima", "61"),
        Manga("Mahou Sensei Negima!", "1331"),
        Manga("Mahou Shoujo Lyrical Nanoha", "13"),
        Manga("Mahouka Koukou no Rettousei", "93"),
        Manga("Maji de Watashi ni Koi Shinasai!", "1218"),
        Manga("Maria-sama ga Miteru", "902"),
        Manga("Mayo Chiki!", "137"),
        Manga("Medaka Box", "615"),
        Manga("Minecraft", "2098"),
        Manga("Mirai Nikki", "1138"),
        Manga("Mobile Suit Gundam Tekketsu No Orphans", "297"),
        Manga("Mondaiji-tachi ga Isekai Kara Kuru Sou Desu yo?", "1403"),
        Manga("Monster Hunter", "1742"),
        Manga("Monster Musume no Iru Nichijou", "683"),
        Manga("Moyashimon", "1509"),
        Manga("Musaigen no Phantom World", "298"),
        Manga("Nagi no Asukara", "1830"),
        Manga("Naruto", "14"),
        Manga("Nazo no Kanojo X", "1247"),
        Manga("Neon Genesis Evangelion", "62"),
        Manga("Nisekoi", "246"),
        Manga("Nyan Koi!", "978"),
        Manga("Oboro Muramasa", "1116"),
        Manga("Ojousama to Maid no Midarana Seikatsu", "1931"),
        Manga("Okusan", "998"),
        Manga("One Piece", "68"),
        Manga("One Punch Man", "188"),
        Manga("Onegai Teacher", "1076"),
        Manga("Ookami to Koushinryou", "958"),
        Manga("Ookami-san to Shichinin no Nakama-tachi", "1244"),
        Manga("Ore no Imouto ga Konna ni Kawaii Wake ga Nai", "328"),
        Manga("Original", "15"),
        Manga("Overlord", "686"),
        Manga("Overwatch", "260"),
        Manga("Panty & Stocking with Garterbelt", "1176"),
        Manga("Papa no Iu Koto o Kikinasai!", "976"),
        Manga("Parasyte", "1911"),
        Manga("Persona 3", "255"),
        Manga("Persona 4", "953"),
        Manga("Pokemon", "148"),
        Manga("Princess crown", "983"),
        Manga("Princess Lover!", "1175"),
        Manga("Prison School", "164"),
        Manga("Puella Magi Madoka Magica", "948"),
        Manga("Queen’s Blade", "656"),
        Manga("Ragnarok Online", "1243"),
        Manga("Rakudai Kishi no Cavalry", "765"),
        Manga("Rakuen Tsuihou -Expelled from Paradise-", "1045"),
        Manga("Ranma 1/2", "767"),
        Manga("Re:Zero kara Hajimeru Isekai Seikatsu", "161"),
        Manga("Real Drive", "261"),
        Manga("Rebuild of evangelion", "149"),
        Manga("Renkin San-kyuu Magical? Pokaan", "737"),
        Manga("Resident Evil", "1118"),
        Manga("Rockman DASH", "1242"),
        Manga("Rokka no Yuusha", "1600"),
        Manga("Rosario + Vampire", "1204"),
        Manga("Rosario Vampire", "234"),
        Manga("Rozen Maiden", "1240"),
        Manga("Rurouni Kenshin", "1520"),
        Manga("Saber Marionette", "73"),
        Manga("Saenai Heroine no Sodatekata", "926"),
        Manga("Sailor Moon", "186"),
        Manga("Saint Seiya", "66"),
        Manga("Saki", "258"),
        Manga("School Rumble", "191"),
        Manga("Sekirei", "180"),
        Manga("Serial Experiments Lain", "1498"),
        Manga("Seto No Hanayome", "219"),
        Manga("Shadowverse", "1957"),
        Manga("Shantae", "1050"),
        Manga("Shijou Saikyou no Deshi Kenichi", "1144"),
        Manga("Shingeki no Kyojin", "169"),
        Manga("Shingetsutan Tsukihime", "16"),
        Manga("Shinmai Maou no Testament", "17"),
        Manga("Shinra Bansho", "18"),
        Manga("Shinrabansho", "2063"),
        Manga("Shinrabanshou", "1979"),
        Manga("Shinryaku! Ika Musume", "1160"),
        Manga("shirokuma cafe", "2074"),
        Manga("Shitsuke Ai", "1630"),
        Manga("Shokugeki no Soma", "152"),
        Manga("Smile Precure", "858"),
        Manga("Smile PreCure!", "1841"),
        Manga("SNK", "252"),
        Manga("Sora no Otoshimono", "20"),
        Manga("Soul Eater", "158"),
        Manga("Space Dandy", "1826"),
        Manga("Spice and wolf", "154"),
        Manga("Steins;Gate", "992"),
        Manga("Street Fighter", "101"),
        Manga("Strike Witches", "642"),
        Manga("Subarashii Sekai ni Shukufuku wo!", "739"),
        Manga("Suite Precure♪", "145"),
        Manga("Super Sonico", "1102"),
        Manga("Super Street Fighter IV", "802"),
        Manga("Suzumiya Haruhi No Yuuutsu", "314"),
        Manga("Sword art online", "147"),
        Manga("taimanin asagi", "1869"),
        Manga("Taimanin Yukikaze", "826"),
        Manga("Tales of the Abyss", "1148"),
        Manga("Tamako Market", "770"),
        Manga("Teen Titans", "21"),
        Manga("Tengen Toppa Gurren Lagann", "1190"),
        Manga("Tengen Toppa Gurren-Lagann", "100"),
        Manga("Terra Formars", "2073"),
        Manga("The iDOLM@STER", "22"),
        Manga("the loud house", "2054"),
        Manga("The OneChanbara", "1053"),
        Manga("The Seven Deadly Sins", "620"),
        Manga("To Love-Ru", "23"),
        Manga("To Love-Ru Darkness", "1135"),
        Manga("Toaru Kagaku no Railgun", "769"),
        Manga("Toaru Majutsu no Index", "741"),
        Manga("ToHeart", "1164"),
        Manga("ToHeart2", "905"),
        Manga("ToHeart2 AnotherDays", "97"),
        Manga("Tokyo 7th Sisters", "762"),
        Manga("Tokyo Ghoul", "864"),
        Manga("Tonari no Seki-kun", "1768"),
        Manga("Toradora", "192"),
        Manga("Toradora!", "1152"),
        Manga("Touhou Project", "24"),
        Manga("Tsukihime", "877"),
        Manga("Uchuu no Stellvia", "715"),
        Manga("Utawarerumono", "1905"),
        Manga("Valkyria Chronicles", "1084"),
        Manga("Vampire savior", "982"),
        Manga("Vocaloid", "912"),
        Manga("Watashi ga Motenai no wa Dou Kangaetemo Omaera ga Warui!", "1032"),
        Manga("Witch Craft Works", "552"),
        Manga("Witchblade", "1302"),
        Manga("Wizard of Oz", "723"),
        Manga("Yahari Ore no Seishun Love Come wa Machigatteiru", "967"),
        Manga("Yakitate!! Japan", "1409"),
        Manga("yatterman", "984"),
        Manga("Yotsubato!", "1429"),
        Manga("Yu-Gi-Oh! ZEXAL", "196"),
        Manga("Yuru Yuri", "25"),
        Manga("YuruYuri", "894"),
        Manga("Zegapain", "1330"),
        Manga("Zero no Tsukaima", "26"),
        Manga("Zettai Junpaku Mahou Shoujo", "1918"),
        Manga("Zettai Karen Children", "1417"),
        Manga("Zoids Shinseiki Zero", "153"),
        Manga("Zombieland Saga", "2059")
    )
}
