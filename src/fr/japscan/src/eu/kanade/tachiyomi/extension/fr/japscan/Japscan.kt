package eu.kanade.tachiyomi.extension.fr.japscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.apache.commons.lang3.StringUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat

class Japscan : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Japscan"

    override val baseUrl = "https://www.japscan.co"

    override val lang = "fr"

    override val supportsLatest = true

    val keysheetChapterUrl = "https://www.japscan.co/lecture-en-ligne/4-tetes-a-claques/volume-1/1.html"

    var keysheet = "0123456789abcdefghijklmnopqrstuvwxyz"

    val realPageUrls = listOf(
        "32084831f17187183348c981992118784058b158a2c837c1c1d199313001e9514718171187c8d1e1a9c128c1b2f8f0b8b39879f1b0d8392/8b378d14830413748f1b89308903817f114788578f0117861ct18705127f2a0a2f9a2d651eu01f2b1847178d2av88129175b28qc1fuf706/88d558bv87f678at5740c90t99f0f0asc93v094130fv7788103049c080962016794s59b859atc736c9ctd798303009e950401919108tc7d/9e8avc7",
        "c57191d45474b09126f1a204122431c1a3c1543115b1c00454444294c3e4820440f1e0b40061c4e4228461843561f3a1d6d1b2d413d1a2b/19651c4a18334b021c4a126618351509427c118614324d124btc163b430f56395f21529f4fu348554a7d4f135bv11956458655q24aud0d0/2128d14v6019412t5003a2at82a3d3es02cv7294d35v70b19323f2a303192339c2asc251321tb0c9025t404193c3e2820343f2e2b30t60c/2e12v80",
        "26b27245b545517227e21375e375020234a28502d602017535f533e5944543f5a1b2e1250122a55543d5122586b2d4d2279213e53462d34/297725572b445c122f5c2c7d2842221f59822b912a4558225et62d405b186c406f3b610c54u2536b528e5f2f6cv52b62579a6fqb5fu4192/a229a23v3100320t11a413ft4304849se32vb37544bv41527424e31474e07400033sa38203dt0100733tf132e4944343f4a4b3e3240t21a/3524vd1",
        "42f84861c121e7e8f3e869314941783820482138c23887e101812951003189c1b7f857d1b7d831e1191178310218606823c889b14078194/8f37881a8a041e7d8a1d8d36810f817b18428a588706128211t9810a1178270b2594256f13u31b2a17401e882ev4872b1e5423q81fu4799/488578cvc73668dte760d9bt290030fs494vf94160cv27e8e0f0e96030464076392s492839ct3786e90t872850003989c0b0f959d0btd73/9e81v17",
        "95313134c4f49031c6c1920412241161c35104e1a50120c474f472b4b3d4d204f0e1e084c09114c492d461d4a5913361061112b4c331c28/1b671246103344011343136b123918094e73108c1c3e4b1e4atf1b3a4c0e5d3657205e9644uf425a4678441353v3145f448657q44bub053/a1f8f17v8009517t90a3d2ft1293e30s729v323433cvf09133c3c2930319231962cs5201e2at0029c27tf071b3b3d2d203f3e2e283ct901/2c19vd0",
        "845000d33313f990c5d021939133d0d0d220931094d00983c3e3a1c3a2a38193a93099134970c3e3f133509324608260053001b35240619/0d5a0e3b05263c9d02300d560f2d0c93396b087f03233f0737t0012330914b2c411c4e8839uc324136683a0c46vf0b463b7b49q838uc943/6047600ve9c8302t59d2713t1122a29sc18v5103d23v19f092c2d122929832d8d1ds2190119td90881cte9a0c2a2a18192a23191124t79c/1e0fv39",
        "83293952e2c2a8091419d0d2c05289191109e239a32968d2c262d072e192f01228492822083982a2002289929349a1c91499b0525169702/97469d2b9d1c2c8592239641991494822b54966596122e9a2ct096152e85381331043c7128u124332053249e37v39e3d276130qa26u4863/f986d9fvc867b95t2831c00t604181as708v203251evc8a9011110d1d1c75187101s00e930at2867d0ct68d971e190f011214020210t388/0a90v28",
        "55c1410494d4d00186214224424491d1e39124c1a53110b404d42264c3a4c214c071e064a0c124f42294b15445e1733116e1f244f381d20/1c6b1a41103c40091244176014311e054e751f86173e4a1240t8103e4d0e5c315227509945u1455e4a7e4f1656v4115247885eq946u50c6/51b8a1evb0a911bt60a302at92a3732s025vc244039vd0d10383224323494399d2es9221c2at3019b20td02163c3a2c213c372e263atc02/2f12v90",
        "93e9e912329278c95469c0d290e279993199027963c96832e2728072d1a2a0823809885238a9823260f279623339f199046960e2e1b9a04/994996269211238a992d964f921b9e87205e9669931c249a2bt59e1025823c15390a367b29u020362f5d209a33v39b30246c35q423ud8e5/e98699fvc897394t7841004t4031f1cs609ve0e2113v9879c15160c1d197e177903s9009706tc86730et788971d1a0a081310080513ta88/0396vf8",
        "5672b20535c5f16287b243a563c5c202e4f20542a6027135b5e5d3f55415f36511126145f142c5b513a5220556e2d4d2977283354442c38/2471245b2b4855182a5a2e76204f2c1b508822962940522558t7264554136841613d6f0254ua5c6f518b5b256cve2d665d9368q659u2159/62f0a24ve110b20t91a4739t0354d4es835v73b5043vc1f26484b344a460c4c003esf30243at017033bte1d2f45413f36414136344ft41c/3b21va1",
        "c59141142474901166d1e25452d4f141a391f41105d140d4f46482743324e2c4e0f1c0141021e4246224f13405f183c1a60142a4e3b1e2a/1c6e164117394208104e156b17321a084378158b173d481842t7103b4f0a533855245b934cu6495d4d724f195bv61958408e56qa48u50b9/6159812vd08931et6063424t4223e39s22cv9244132v70911363d2e35359d3f942as92f1120td049d2ft6081733322e2c3e3f2c2131t20e/2216v20",
        "96b2d2a565b5a1f25762a375731562d2a452954266e211c5a5c513d594156385b14251c551e235d5531542f566a254527762f315d422a3d/267f2153264c581d2f5729712d4b2f1b54842c9e2b41502b54ta22495317684c6d3e6d0a53ud52655789572c6ev8216b559962q65eua191/32e0c2cve13032ate174c33tf37424bsd39vb3d5a46vb1a2f45463a474701460d3as5392436te110c3atc112d494136384b44353c45te13/3d25v11",
        "b612929525d5816217a25365d355a2f2b42285726692f1d5b50563c584a59395d172f105716225b553b5526556425412777233a534f2a3d/217b2859214b5a112f562d762e492f1f5b84269b2f42522c58tf2d4d561b64446334690a5cu7516b5e805e2b63v4246d579e6bq959u1152/123052cva1b0d27t2174735t7394746s13bv1395942vd1826414a35464d054a0f3bs2382736t91f0d3bt0162c484a39394d473f3047t612/3b25vb1",
        "2349699292e2b8397439c0c2b0a2f97911d9623933c95842a2321002a1c270b2680938f20809b22200d2d90203c971b9c4095022e1e9e0a/9f409e24961326829c289e4194139689295a94679f1a289427tc9b102288351a360d327021u42f332452209a3cv093332f6136qd20u08a0/6907a9av4817b95t08e1b0etf0a191bse02v4062919ve8b9317130c1c1b7a1f7701sd069303tc85740at381901a1c070b1610030f10t08b/0290vd8",
        "82887871f161b708c35839b129f1d8d8f038b1b842e89701e1f1b901c0a19941e71897d19758b1d1d991e8e14248b0b893c82971a07879a/8d32881d830c117a891f82368708847f1f408a5d8d02168d16tc840c177b21012c90286b1fu71425194c15812bv98d2d1e5e22q61bu8750/c8f6f8cvb796a8bt971039bt3980407s198v897170fv67b800c05930b026f0d6d9fs39b8b94te79609etf7b800c0a99940e01999d09t57b/9d8dv97",
        "825858f1f161f7b8c3e8a97199913808d018714802e8571151e179912041e901a7b8a701f718d141b9a1f88192a8f028e38879818028890/863780178e08127888128a3b8f088f751847845d880f1a8116t38b071d70240a249b236612ub1d2f1d4d128626v58527195122qd15u0731/c896d8bva7a6785ta7d0f9ctf930f07s598v5951f0fv67f8b0c0e9a07096903609ds1978490te756195te778902049e900a0b9a900ft17d/948bva7",
        "65511154c4f4e06136310264b2241101b3215461551140442484b2b4131472c4f03190d490a144343204f104c5e1b31116c1825443c1b28/176e134816384b061841156a17361d084f7a1b86113f451a4bte113448045130572759994du3445c4077421953vc1556468550q04du20c5/e199918v4019c14t6083526tf203034sf26v521453cvf0e16333320363b9231902bs2251625t1049422t80b1b3131272c3f33292d39ta04/2313v00",
        "226868713101073873f80931c9a1b8785048b1b8a2483781c141794120e129a137a86771b7a88131f9f18861c208007883185931e008098/823a811f89011e71891e8e3e860c8e78114d895e8c071a8416t98c00117e2002259a2f691bu01127134e198d20v58423195428q614u8773/a866582vb766683te7c0892t19d000csc92v6961703v07083070f90030c6a0b6795s49b8b9at473689ct47784020e929a030a96970bta78/938fvf7",
        "b6422275d59531a28752d36503c5723224f2a5b2a6925125451573f54495f3d551c291a541b2955583a5f245169284921762837524c2b31/277128502a4656182455267f2a4d29105e8f2096214354295bt922445716694767326b045euc586059805d216cv5266d54926bq056u4148/c2e0f21vd130424t513463ctf324c4fsa3bv432574dv9132a48453d46400c470332sf3a2b3at9150234t1172f44493f3d454c393a44tb19/3528va1",
        "55c1b1841434c0719661827432649121b3a14481a5f1d0b43464323403f4e204c0e11024006134845204d1847561c3f1a6319274534132a/176c1847173b4401194d1664153b1e0d40751a85193c4a1d4fta1e3a460b5739532f529d47ub4654467f44115fv7175843815fqa45u5038/c120716v50e9d17t60c3f26t023303fs025vc2b4831v30c1739362837339639922bsa24182atf0d9b23t60313303f2e203c3e212230t603/2815v00",
        "c5e1a1242414b03196d1e2447264a1d1d3b1a4a1c5d1d0441414f24413c4527450512044d0710464b2b491746571532156315224836142d/15631f42153f4b031b411567153f1e08417b178e1c3a40114dt01136450c563f532f599b43uc4b53437147105bvd1352498959qd49u00c9/0100619v7059910tb0d3f29t2293f3ds82cve2a4232v10b13393d2e3437963a9d2dsb2a1a2ctd0d9421t10f14313c2527353522243dt700/261bvb0",
        "872333a6868652e3086304262476235325d3262337a3a24636b624566556f4f6b2837226e2331696e4f6e316c7f335b378a374c615b3146/388c3e6135586322336d3e803e5a33276e973f09395a683668t838596e2179557c4c741765u66a7d6895633172v0327b680472q26fu32c2/d3a253ev925163at7235449t34e595ese48v2436a58v8253e505640525217521542sd423243ta2a1443tb223556554f4f5b5847425et321/493evf2",
        "b64262d565f57172c792b3b5c3e5220294e215e23652c1c5a555b3b5f49513a561c2d105111205056375f27516e23402777213353402233/217f28592f485016285c2b73244320125c8f2290284950255ct3204356136a4664386d0653u558695483572f62v2296b539465qc56ue152/124162cvf1a0420t21c4031t63f4940s73bv4365d46vf17274c493b4b4c0e420039se312e33t51c0c3at51b2b4f49313a464c3d3041t110/3026v71",
        "c52121f41454a021e6b1e2c4a2b45141a3a14471a571804444d42294c31402e43081a0048041a4d48294c10435919311062172b463d142d/1262104e173a410c154810611b39140840701483163b4a1e4btf1c334605593e5028569c4cuf4b53447c4b1356v0155f408258qb4fu50a2/919041cv20e931ctb00352btb223030s02cv2224f31v50a123e3b2e3c3a9b35942asa24172at7089424td02193c31202e33382a2038t40a/2d18v90",
        "87a3234666d6d28338d32476b4b68343b583c613f7e3c2b6c626f49625d6a4b6f26352e6521356c6c446e31697f37513e84354f6753374d/318b3f643753612f396f3f86315e3b246d99360c3850673768t7325f642c71597f417b1f66ub697f6a906d3e77vf3575610a74q16bub235/03e2e32v62d1831t1275b42t04e5151s148va426456vd2d38535d42575b1b58144bs84c314fte2c1b4ct22f39525d4a4b5f56454e55t125/4c3cv42",
        "46b282857595e10277c2c3b58375e2427422c582f6229185052583650445a31541926105816225f543b5f2c5b682e4c2a7c2c36524b2b38/297b2f5d234e511d2b5f21712b4d201656882698234f58205dt3214f581d62466134660251uc5160548a552d64v923655e916bq758ua135/a2f1928v9150b21t9134734t0304746s334vb385847v91e20474c3c4b48074e0437s23c283ft2190830t2182640443a314449363048t612/3f24vb1",
        "2208f811b1b127e813282921b9110888d0088188e2f8a7e181a119e19071c9c187688731370831f119711841626880787388c9a170a8e91/8930871d82051d758c19823a8c0382741242885289031c8f18td840313702604279a236618ud1621134816872ave822b1a532fq610u2782/08f708fvd77648atd780994t0900305s992v09f110bvb728e010292020b6100689ds098889etf7a6e98ta718e09079c9c0806989303t073/9f81v77",
        "0309e92292a258a9b459804250022909a139a29963e94812126230d2c122e0e2c84968f2e81972f290725962239941496449f052c159206/9043902497182987952f98479214908f2c5f95629915259b2ct2981d2c81391a3a06307429ud2c3f2356259c32v2973d216235qe20u68f4/5998792v08b7e96t38a1b06te061715s700v00e2219va859a1b150814157012700as30a9906te847101t6839d1c120e0e1c14060f1et187/0f99v78",
        "161292b54535e162f772830583f56262c49205123662e145d58593d514a5e3a53142b1e571c295959335c2a586d25492c722b3951422b3e/2e722b5e2c4157132a542c772a4e281c57802f952144582151ta24425511614e6f3e610c58u0576b5a835a2665v32d6f589665q950u91c8/c2e1e25v21f0d20t21d4637t4394e44s931v1395b44v31e264f473840480f46063cs9302133t61e043dt8192d414a3e3a43443b3e47tc19/3929v31",
        "5723734666964203287354a664f683335533c673d773a2b6567624a6b566d49632c392a66253a676a446538657430503b86374f6c583b45/358f336834526d243e62358b345b30206b9f3d033c526e3e63ta3e536b287457764f711869u66c73619b68387dv53e766c0275qe66u7290/e3b3c31v2231839tc275949t14b5352sd45v2476456v924305257455a561f581345s34c374dt72a1b45t7223a5b564d49535c494a56t52a/473av42",
        "046000d3d3a3192005b0f113e1e3d0702230f3007450d943438331f382a301a329a0e903d92053c381c3b08374400290a5e021a3327041a/065f033d03293b940f3c035705250e9e326b04790227320533t1032632954f214216478e3cu7324f3460350f45v70148337141q339u8928/d0a0107v9998b08ta9c2d18t31a2725s610v6103d2dva9102202b1f212e8e2d8712s31f0017t59d8414t8930f282a101a222a1e102dt295/1c08vc9",
        "45317194148440710671c2e482c4610193f174910591d0f4643422a45314d23420f140840021f4b4b2f4d194b54133517671d2b40371a27/1e631c4717344f0e194d116d1c361f06497b1e89103548164et6103c430d5f3c5b2f5c9446u94154447b4c1458vd155d478a5eq548ua0f0/0131a14v104991etc073b2ft52b323es024v3274931v8041730372c3e389c369029sf271920t90d9f26t3021a35312d23323f242830t20f/2b1bvf0",
        "06b2d2b585a561b2673233e5b36582a29442d5629602c12565e58335c4052395c17291b531b2b535f3154285560204423702039584f2f36/227d2059254953182d52287023412318508228912847562c53t029475c176c4d6b3967055bua5f6d568653276dv6226c5b9a60q35duf1f2/0252c20vd13052btb11443bt4364b43s430vb3d5b48va162b4643334e4b06480a39s43d2639t01c0236te18234c4032394c47393b43tb1b/332fv11",
        "d400d0e3f38379f08540c15371a3a04042f04310e4b0f943f3735163e223a15399303923490053f3418320838460d230f5700163c2a0214/05510e320d213b9202330b510d2a009630600977072b350c33t60e2d369341204d164d8c3eu938453a6d3a014dvf0043347f42q03fuc941/90c0003v195820et3972e1ft21d2123s21dv01d3e2fv8970f28241c25278a2a8414sf14011etb9f841ft795062e221a152923131224t095/1f04v89",
        "577303969626a243881304c6947613f3d5a30603178302a606064476a51694f6f213f2a692f3f63664b643462783253378130416e533041/3381316b315b622a36633b823d5734286091310237586d3366te385b6c247957744d771067ue6475699f683073v93a7b670277q06cu8265/f3d3630v02b163atb2d5d44t349545cs345v7406959v22a345851405c5917511f4dsa403041t8201a40t024375a51494f5f514f4a59tf2f/4336vb2",
        "06b272b515b591b2473283850375f2f234023522e6d2f1e58575834524451315a1024125f19295f563352235c6e2b4724712933564a2d38/297b24512f47541b2559297b2f482f19538d2494264f5a2c52t925455e1b6a496c306f0950u153695880582b68v92160599062q95bu9195/a2f2c21vd180e27t3154a37t8334041s930vb375b41vb192b4443384840074f0f33s033223etd1f0e38t71824424431314a4034324ft919/3f26v31",
        "363212859535712237b213f543151272c4c27542261201c50555d3a5a48593d58152c16551d2d5d57385b2d506b25432179213e52452238/2a7a29552c4752182e512b7a2f4d291f598c29912149522f56te2d4b5219624f6c3f6b0d56ud58625c8d522062v02c615d9268q255u8136/22d2527v3100f2dt6134d3ct4364c4asa33v3315849v31722434b314f440141073csc372432t1100c30t51d2a4a48393d48453c3645td1d/3d27v81",
        "f2b8d8213131f708b3d8f941298198b8c028c1d8a26827e1b1d1a99100612981f77877410778c121b961e821e268e0485368a9017078999/813080168d0b1c76811b8330850e817f144d8f5f8e031a8516t4810b1f7e2d012194266410u114201942158426v482201b5c2cq315u6713/f8b8283va716b85tc75059dt59e0604s39fvb9d1203v37f800b0d9f040268096b9cs29c8d9at6726e9btd7a89000692980f07979400t77c/928bv67",
        "a72353468616f27358f3a4c624b6e3131583e613a7236246c60604160546e4568263e256c2e386760426f3c6873365739833b4b6259334d/368d3f6334536e26396132853d5b3a216e9735023b51653c6ft1345a60257d597d4a741e60uc6e79689666347ev4317e610774qc6fu5249/0383a37v9231e35t2245549t244545fsc4av2456458v12f37555f4a5c521b5e1141s84e314at226144ct0203150544e4558564e455cte28/4730v22",
        "c581814464e4c0f18611d22422c4710143612431e5813004249482d4d3f482f45011c004709174e43284411465d1e38176c192648371f2d/146214411937410f104e1c6e1238110a4a7c12811c394a1648t6173c410d563b5121529e40ue49544b70441c5bv310514b8f58q54bu40e8/d1a2810v2019311ta0a3c21t8223239s22cv8284436ve0c1f38312d32329c379024s622132et8039022t9081d3d3f282f35312c2037t907/2e13v80",
        "a79363e6b6367253a853f41674a6b3c305c3b65347431246a606e4c685d6e436e2d32246022366d6c4367326978375236873841625c3940/3b833866345a632f36633e893d58392c679a3207325b6b3168t43759682473537144771568u063796f976a347dvb357f630773qa66u7251/0354639v4221437t3265445t9465256s94av9466e5bv327355a554f51571a5b1c40sc4b3544t421144at02e3c585d4e435e5d424450t226/4d3cv32",
        "937919e272b228d9f4e9a062d082d9d94149725913d938c2326220a2b1b200b2981978b2c8f9c202700299229319c189b449d0521149409/994d972a99182a8296259e4e9e139c8b20529e699c1e249d2dt5981b228e32193c0f3b7722uc27382656209e38v5953327613fqd27u0868/d970099v583709et2871307t00a1f11s009v7012e17vb829d1f1e0a161d781d7d04s4079501td837c03t6829a1b1b000b1911070b1ctf8c/0097v08",
        "379373a686e66293881354560476c3c3a5337633173322d6b6a654a625263496a22362c66203267604c6c34667f3553308f30426f553346/308e306b365b632e3d663a84325b3e2b689138033f556c356ft4385d6c2770577e437e1763u16271629f69307avb39796d087eqd6eua253/336493av92b173et0205d40t2425f5asb43v9476a58ve26395851455550175c1c4as3473341t3221d4bta253a525243495a52464c56t022/4730vc2",
        "f2a83891e101075823c879e129319848e04811b8d208c75151c1c981f061697157386711978841010981e8b1e228f098239839c15068798/86318f1b870e117e8e188a318207857f1b4b815f860512871bta880e1e732e052d962b6a16u914281a4e1f882bve882f125f28qe1auf749/7859884v97c698bt07a049eta900f0ase9fva93190ev07085020c970e026309649es4918b9dt07c6595tc7c880f0696970503969109t874/9080v87",
        "15c1c154f40490a18641929422e401519381c4c105d130b49494428443a49234e0e160949021746452a491e4c5f1d3e196c1a2c473f122e/106e1b4c1033460b1544156e1e381800427c188e1c35431a41t81a3341045e3250295f9f47u54b594a7f441a5cv21a534f8352qc41u8073/71b2511vc039b1ct7073721t72d3635sb21vc2c453fv0091a38342939329e309529s82c1c20td039b29t90418343a29233e3e262939t207/2615va0",
        "87039376a61672b3f8a3e496946613a325e3f653771322f6e60654a62596e4a65293a24652a33656e476c31657e3a59318237426c5a3b41/38803065385766243b69348e355e362d649e3c0e3757653362t7355e6f2978547d40701f64u9607360976f3379vb3f79620f78q461u72e6/639493cvd251830t62f5144te4b5957s948v049675av1273b5f5a4e595916511a42se4f3547t1221f4et0253a52594e4a55594a4455ta23/453ev72",
        "e3b9d96202c2c879248910628092894951696229132998e27242c012f19250229869f802f8d962f240425932b3b981a9a439f062d189407/9e46992a9011208794279a409e1a9a862c529266951f239223td9e15258f31153008357d23u72b382d54239637v39434226832q627u78a3/0950196va867c99t9801f00tf0d1510s00evb0d2610vc8c97121801161879187405s6069201t2897e07t48c911f19050219160f001ftd86/0f94v48",
        "32f8c841f181379823f8d9f1f95108b8e0087148c25847a111d1c98150512971e73887a1a7e80191a9a1c8f1b2f860c85328991190c839c/8a348c128e08147a8f1784308008877d1b40815a8601118311t08b0a1678280f25982c6c11ub13261d4d128223v78f291e5a24q515ub753/e889586v97e6688td770e9at8960601sf93vf9c140fv87389020f9d0f0f65006b9es097849ct5746a91td7c88050592970e03989a0ate70/998ava7",
        "f24838a1016177d8c3787951b981d8c8903821a822e8f741317159a1b0a1993177d8771137d88101a9210891123890482338895160c8e90/8c3b8a1e800d1f7a8f158331860d8a7319458a508f0c1e8619t3810e157f2909249e25631auc1c281049188223v88c28145e26q619u0704/e8d968av07b6d8ct8750a9dt5950306s39fv4931a00v6778d0c0797050b680d6c99s3928a92te7f6493t7758a0b0a9993070d979103td78/908av27"
    )

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val indicator = "&decodeImage"

        val request = chain.request()
        val url = request.url().toString()

        val newRequest = request.newBuilder()
                .url(url.substringBefore(indicator))
                .build()
        val response = chain.proceed(newRequest)

        if (!url.endsWith(indicator)) return@addInterceptor response

        val res = response.body()!!.byteStream().use {
            decodeImage(it)
        }

        val rb = ResponseBody.create(MediaType.parse("image/png"), res)
        response.newBuilder().body(rb).build()
    }.build()

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy")
        }
    }

    fun loadKeysheetChapter() {
        var response = client.newCall(GET(keysheetChapterUrl, headers)).execute()
        var doc = response.asJsoup()
        createKeysheet(doc)
    }


	//Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas/", headers)
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        pageNumberDoc = document

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = false
        return MangasPage(mangas, hasNextPage)
    }
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector() = "#top_mangas_week li > span"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()

            val s = StringUtils.stripAccents(it.text())
                    .replace("[\\W]".toRegex(), "-")
                    .replace("[-]{2,}".toRegex(), "-")
                    .replace("^-|-$".toRegex(), "")
            manga.thumbnail_url = "$baseUrl/imgs/mangas/$s.jpg".toLowerCase()
        }
        return manga
    }

    //Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .distinctBy { element -> element.select("a").attr("href") }
            .map { element -> latestUpdatesFromElement(element)
            }
        val hasNextPage = false
        return MangasPage(mangas, hasNextPage)
    }
    override fun latestUpdatesNextPageSelector() :String? = null
    override fun latestUpdatesSelector() = "#chapters > div > h3.text-truncate"
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    //Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isEmpty()) {
            val uri = Uri.parse(baseUrl).buildUpon()
                .appendPath("mangas")
            filters.forEach { filter ->
                when (filter) {
                    is TextField -> uri.appendPath(filter.state)
                    is PageList -> uri.appendPath(filter.values[filter.state].toString())
                }
            }
            return GET(uri.toString(), headers)
        } else {
            val uri = Uri.parse("https://duckduckgo.com/lite/").buildUpon()
                .appendQueryParameter("q","$query site:www.japscan.co/manga/")
                .appendQueryParameter("kd","-1")
            return GET(uri.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector(): String? = null //"li.page-item:last-child:not(li.active)"
    override fun searchMangaSelector(): String = "div.card div.p-2, a.result-link"
    override fun searchMangaFromElement(element: Element): SManga =
        if (element.attr("class")=="result-link") {
            SManga.create().apply {
                title = element.text().substringAfter(" ").substringBefore(" | JapScan")
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        } else {
            SManga.create().apply {
                thumbnail_url = element.select("img").attr("abs:src")
                element.select("p a").let {
                    title = it.text()
                    url = it.attr("href")
                }
            }
        }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#main > .card > .card-body").first()

        val manga = SManga.create()
        manga.thumbnail_url = "$baseUrl/${infoElement.select(".d-flex > div.m-2:eq(0) > img").attr("src")}"

        infoElement.select(".d-flex > div.m-2:eq(1) > p.mb-2").forEachIndexed { _, el ->
            when (el.select("span").text().trim()) {
                "Auteur(s):" -> manga.author = el.text().replace("Auteur(s):", "").trim()
                "Artiste(s):" -> manga.artist = el.text().replace("Artiste(s):", "").trim()
                "Genre(s):" -> manga.genre = el.text().replace("Genre(s):", "").trim()
                "Statut:" -> manga.status = el.text().replace("Statut:", "").trim().let {
                    parseStatus(it)
                }
            }
        }
        manga.description = infoElement.select("> p").text().orEmpty()

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapters_list > div.collapse > div.chapters_list"+
            ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW),.badge:contains(VUS)))"
    //JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs/US versions and replace them with a translation as soon as available.
    //Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.


    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.ownText()
        //Using ownText() doesn't include childs' text, like "VUS" or "RAW" badges, in the chapter name.
        chapter.date_upload = element.select("> span").text().trim().let { parseChapterDate(it) }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date).time
        } catch (e: ParseException) {
            0L
        }
    }

    fun createKeysheet(document: Document) {
        val pageUrls = mutableListOf<String>()

        document.select("select#pages").first()?.select("option")?.forEach {
            pageUrls.add(it.attr("data-img").substring("https://c.japscan.co/".length, it.attr("data-img").length - 4))
        }

        var az = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray()
        var ks = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray()

        for (i in 0 until realPageUrls.count())
            for (j in 0 until realPageUrls[i].length) {
                if(realPageUrls[i][j] != pageUrls[i][j]) {
                    ks[az.indexOf(pageUrls[i][j])] = realPageUrls[i][j]
                }
            }
        keysheet = ks.joinToString("")
    }

    override fun pageListParse(document: Document): List<Page> {
        loadKeysheetChapter()
        val pages = mutableListOf<Page>()
        var imagePath = "(.*\\/).*".toRegex().find(document.select("#image").attr("data-src"))?.groupValues?.get(1)
        val imageScrambled = if (!document.select("script[src^='/js/iYFbYi_U']").isNullOrEmpty()) "&decodeImage" else ""

        document.select("select#pages").first()?.select("option")?.forEach {
            if (it.attr("data-img").startsWith("http")) imagePath = ""
            pages.add(Page(pages.size, "", decodeImageUrl("$imagePath${it.attr("data-img")}")+"$imageScrambled"))
        }

        return pages
    }

    private fun decodeImageUrl(url: String): String {
        val az = "0123456789abcdefghijklmnopqrstuvwxyz"
        // skip https://, cut after next slash and before extension
        var urlBase = url.substring(0, url.indexOf('/', 10)+1)
        var extension = url.substring(url.length - 4, url.length)
        var encodedPart = url.substring(url.indexOf('/', 10)+1, url.length-4)

        return urlBase+encodedPart.map { if (az.indexOf(it) < 0) it else keysheet[az.indexOf(it)]}.joinToString("")+extension
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun decodeImage(img: InputStream): ByteArray {
        val input = BitmapFactory.decodeStream(img)

        val xResult = Bitmap.createBitmap(input.width,
                input.height,
                Bitmap.Config.ARGB_8888)
        val xCanvas = Canvas(xResult)

        val result = Bitmap.createBitmap(input.width,
                input.height,
                Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (x in 0..input.width step 200) {
            val col1 = Rect(x, 0, x + 100, input.height)
            if ((x + 200) < input.width) {
                val col2 = Rect(x + 100, 0, x + 200, input.height)
                xCanvas.drawBitmap(input, col1, col2, null)
                xCanvas.drawBitmap(input, col2, col1, null)
            } else {
                val col2 = Rect(x + 100, 0, input.width, input.height)
                xCanvas.drawBitmap(input, col1, col1, null)
                xCanvas.drawBitmap(input, col2, col2, null)
            }
        }

        for (y in 0..input.height step 200) {
            val row1 = Rect(0, y, input.width, y + 100)

            if ((y + 200) < input.height) {
                val row2 = Rect(0, y + 100, input.width, y + 200)
                canvas.drawBitmap(xResult, row1, row2, null)
                canvas.drawBitmap(xResult, row2, row1, null)
            } else {
                val row2 = Rect(0, y + 100, input.width, input.height)
                canvas.drawBitmap(xResult, row1, row1, null)
                canvas.drawBitmap(xResult, row2, row2, null)
            }
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

	//Filters
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class PageList(pages: Array<Int>): Filter.Select<Int>("Page #", arrayOf(0,*pages))
    override fun getFilterList():FilterList {
        val totalPages = pageNumberDoc?.select("li.page-item:last-child a")?.text()
        val pagelist = mutableListOf<Int>()
        var filterList:FilterList = if (!totalPages.isNullOrEmpty()) {
            for (i in 0 until totalPages.toInt()) {
                pagelist.add(i+1)
            }
            FilterList(
                Filter.Header("Recherche par Duck Duck Go"),
                Filter.Header("Page alphabétique"),
                PageList(pagelist.toTypedArray())
            )
        } else FilterList(
            Filter.Header("Recherche par Duck Duck Go"),
            Filter.Header("Page alphabétique"),
            TextField("Page #", "page"),
            Filter.Header("Appuyez sur reset pour la liste")
            )
        return filterList
    }

    private var pageNumberDoc : Document? = null
}
