package eu.kanade.tachiyomi.multisrc.mmrcms

import android.annotation.SuppressLint
import android.net.Uri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import android.util.Base64
import java.net.URLDecoder

abstract class MMRCMS (
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private var sourceInfo: String = when (baseUrl) {
        "https://onma.me" -> """{"language":"ar","name":"مانجا اون لاين","base_url":"https://onma.me","supports_latest":false,"isNsfw":false,"item_url":"\u003c!doctype html\u003e\n\u003chtml lang\u003d\"en-US\"\u003e\n \u003chead\u003e \n  \u003cmeta charset\u003d\"UTF-8\"\u003e \n  \u003cmeta http-equiv\u003d\"Content-Type\" content\u003d\"text/html; charset\u003dUTF-8\"\u003e \n  \u003cmeta http-equiv\u003d\"X-UA-Compatible\" content\u003d\"IE\u003dEdge,chrome\u003d1\"\u003e \n  \u003cmeta name\u003d\"robots\" content\u003d\"noindex, nofollow\"\u003e \n  \u003cmeta name\u003d\"viewport\" content\u003d\"width\u003ddevice-width,initial-scale\u003d1\"\u003e \n  \u003ctitle\u003eJust a moment...\u003c/title\u003e \n  \u003cstyle type\u003d\"text/css\"\u003e\n    html, body {width: 100%; height: 100%; margin: 0; padding: 0;}\n    body {background-color: #ffffff; color: #000000; font-family:-apple-system, system-ui, BlinkMacSystemFont, \"Segoe UI\", Roboto, Oxygen, Ubuntu, \"Helvetica Neue\",Arial, sans-serif; font-size: 16px; line-height: 1.7em;-webkit-font-smoothing: antialiased;}\n    h1 { text-align: center; font-weight:700; margin: 16px 0; font-size: 32px; color:#000000; line-height: 1.25;}\n    p {font-size: 20px; font-weight: 400; margin: 8px 0;}\n    p, .attribution, {text-align: center;}\n    #spinner {margin: 0 auto 30px auto; display: block;}\n    .attribution {margin-top: 32px;}\n    @keyframes fader     { 0% {opacity: 0.2;} 50% {opacity: 1.0;} 100% {opacity: 0.2;} }\n    @-webkit-keyframes fader { 0% {opacity: 0.2;} 50% {opacity: 1.0;} 100% {opacity: 0.2;} }\n    #cf-bubbles \u003e .bubbles { animation: fader 1.6s infinite;}\n    #cf-bubbles \u003e .bubbles:nth-child(2) { animation-delay: .2s;}\n    #cf-bubbles \u003e .bubbles:nth-child(3) { animation-delay: .4s;}\n    .bubbles { background-color: #f58220; width:20px; height: 20px; margin:2px; border-radius:100%; display:inline-block; }\n    a { color: #2c7cb0; text-decoration: none; -moz-transition: color 0.15s ease; -o-transition: color 0.15s ease; -webkit-transition: color 0.15s ease; transition: color 0.15s ease; }\n    a:hover{color: #f4a15d}\n    .attribution{font-size: 16px; line-height: 1.5;}\n    .ray_id{display: block; margin-top: 8px;}\n    #cf-wrapper #challenge-form { padding-top:25px; padding-bottom:25px; }\n    #cf-hcaptcha-container { text-align:center;}\n    #cf-hcaptcha-container iframe { display: inline-block;}\n  \u003c/style\u003e \n  \u003cmeta http-equiv\u003d\"refresh\" content\u003d\"12\"\u003e \n  \u003cscript type\u003d\"text/javascript\"\u003e\n    //\u003c![CDATA[\n    (function(){\n      \n      window._cf_chl_opt\u003d{\n        cvId: \"2\",\n        cType: \"non-interactive\",\n        cNounce: \"13997\",\n        cRay: \"627b6f1fc94f0cb5\",\n        cHash: \"35ddd2c36861109\",\n        cFPWv: \"g\",\n        cTTimeMs: \"4000\",\n        cRq: {\n          ru: \"aHR0cHM6Ly9vbm1hLm1lLw\u003d\u003d\",\n          ra: \"b2todHRwLzMuMTAuMA\u003d\u003d\",\n          rm: \"R0VU\",\n          d: \"perjLNesnwec2J0ed2/ySdERf73jucfZwEQVLgVcXCGHw6P8O0jLroAf/zm8b7CW0V2fkpmkuAX4i7lfeGA2/qlZWIZ2vl9louVQmNQigRyZPcsZEkh4akKpE3OdrHGDWXtmyStN+72Mnupcu58bOAmovZgc5uUasxD+DI2+QWBnzpvDLiEbf67enhIfZ4oBE4edpmuMChSXZ64U/pKg4YIEelxN3q+xNRAEym0/oYEpM8ZmemZmk9gKSolAclvI/DqOzUTXwC+OSkurwP+AJLAyJVdIFNRKnWFNl4ejWWltvVLGoak36pZEVLZCb19WjoQsJxH4pmhKDOadVuEYEFXCi3qLfzDmGP+gYfRDEvCghIpX7XIkKQ/t/Di42dO08LDHv6wz5v6aMznDLbZywGzaVURLsfxJbYUq7V+H3pdvJ80CxkBnN6tQEqZmyHtdagaj3yNI+mSFe8rc/Q+P3YRTujOJqhOOu0/mDZJ8Ry3nEBUxDkN8sjoHZ8Njg4eMYiStC8tw+Q0ln+VuWMzJJcS6YH7fNV4sRiAOcbjH3QVFduktPfaboEl+FHYbZB1kQI5g41mAolX1r65Q5caV1A\u003d\u003d\",\n          t: \"MTYxNDM2MTA0Ny4wMTEwMDA\u003d\",\n          m: \"eLfqUZUe/r8pjE8cGOE1QCiB3boeRVj2i/NtmEntI4Y\u003d\",\n          i1: \"lzjtnFo2kjaX0kiLi4lxUA\u003d\u003d\",\n          i2: \"gQvCXATP8aQ7FLndAC/M4Q\u003d\u003d\",\n          uh: \"QNqr1PtsmAsBuHIaoM6zeJRgUdRT1sK83/SuOuA+LQM\u003d\",\n          hh: \"igG83LHMfnEqFW43t5rmZJNdy6qUZ3mbsE1OaNp3q7o\u003d\",\n        }\n      }\n      window._cf_chl_enter \u003d function(){window._cf_chl_opt.p\u003d1};\n      \n    })();\n    //]]\u003e\n  \u003c/script\u003e \n \u003c/head\u003e \n \u003cbody\u003e \n  \u003ctable width\u003d\"100%\" height\u003d\"100%\" cellpadding\u003d\"20\"\u003e \n   \u003ctbody\u003e\n    \u003ctr\u003e \n     \u003ctd align\u003d\"center\" valign\u003d\"middle\"\u003e \n      \u003cdiv class\u003d\"cf-browser-verification cf-im-under-attack\"\u003e \n       \u003cnoscript\u003e \n        \u003ch1 data-translate\u003d\"turn_on_js\" style\u003d\"color:#bd2426;\"\u003ePlease turn JavaScript on and reload the page.\u003c/h1\u003e \n       \u003c/noscript\u003e \n       \u003cdiv id\u003d\"cf-content\" style\u003d\"display:none\"\u003e \n        \u003cdiv id\u003d\"cf-bubbles\"\u003e \n         \u003cdiv class\u003d\"bubbles\"\u003e\u003c/div\u003e \n         \u003cdiv class\u003d\"bubbles\"\u003e\u003c/div\u003e \n         \u003cdiv class\u003d\"bubbles\"\u003e\u003c/div\u003e \n        \u003c/div\u003e \n        \u003ch1\u003e\u003cspan data-translate\u003d\"checking_browser\"\u003eChecking your browser before accessing\u003c/span\u003e onma.me.\u003c/h1\u003e \n        \u003cdiv id\u003d\"no-cookie-warning\" class\u003d\"cookie-warning\" data-translate\u003d\"turn_on_cookies\" style\u003d\"display:none\"\u003e \n         \u003cp data-translate\u003d\"turn_on_cookies\" style\u003d\"color:#bd2426;\"\u003ePlease enable Cookies and reload the page.\u003c/p\u003e \n        \u003c/div\u003e \n        \u003cp data-translate\u003d\"process_is_automatic\"\u003eThis process is automatic. Your browser will redirect to your requested content shortly.\u003c/p\u003e \n        \u003cp data-translate\u003d\"allow_5_secs\" id\u003d\"cf-spinner-allow-5-secs\"\u003ePlease allow up to 5 seconds…\u003c/p\u003e \n        \u003cp data-translate\u003d\"redirecting\" id\u003d\"cf-spinner-redirecting\" style\u003d\"display:none\"\u003eRedirecting…\u003c/p\u003e \n       \u003c/div\u003e \n       \u003ca href\u003d\"https://robinsonsdrlg.com/direct.php?tag\u003d7\"\u003e\n        \u003c!-- table --\u003e\u003c/a\u003e \n       \u003cform class\u003d\"challenge-form\" id\u003d\"challenge-form\" action\u003d\"/?__cf_chl_jschl_tk__\u003d5632e90e17b735f2c60967782a1f177ada357135-1614361047-0-ARPv9TopkAeMJMPfWBiIqdvb-uCqLMoeuuQyUltx6UrNX4zWULStKgyNQNUGDMH1-kQuXNGZ_cR0dHtq9nO2MaJTRQsGLEeaOSAB6RvjE2eQmAxMqsHZHD7tSE3TWaEgzVFYyM4gGQL-IUkEyWrplnVHHcPnQ8AfSiQSo9EOv3XoKRMsa20P6EeMOBNc2S4dkLZglyaKpvSPormJ3WNggosqmHnJzvDJ0dI_zOBqeNJmRpybbZwX7VrrDEdjevj-q5Wd7MtJ4lbzhvUEzLI6bJBbBjmrvfvUnRT7W0hMoJwMXaG_hnmGxar3yo49ojlwp4K0ZXYALMX-i97hDuP-I2M\" method\u003d\"POST\" enctype\u003d\"application/x-www-form-urlencoded\"\u003e \n        \u003cinput type\u003d\"hidden\" name\u003d\"r\" value\u003d\"9a1ec5ad9f1dd181ffe461c78386a14b693b3f44-1614361047-0-AU4jEZeN9ma/p3ARxE78JE/WQ5mZfcB6LzyNUxA8oal79Z2zA9rnH64q+//74kM0URJoCeRNVN3F1tZ1DYNlYMxTe1G3pY+zJAUgkEk02xg4sA1dZTdM+hUdrKi4mIIl4plWFQVN7GuRoBWbVWhFTgvQ/F2kgFYBs5w5W8j5WCukeMtexMIJNkAalO+ETfIJpl2H7BfFZG1CyUslcjAe2u26lcpBnQVKa3eI9qTJE27+/5C/HMfOJSzJoqQYvXxiMQvYNWUOJQc+yOfC0AqFG7CoOnNgwE5YP4PFCbMojAv4rjsC90h07Eh/qULuThzbK/ZYARaqSDx17yE0A0JAeiHd6ZYkK1+NtRVipsUOA54wtVBJOzBefb75mUB4ui1kTuySPWZD6KxsT5YO4vHmQEUJfulsRWHD2MqvNeK2PChjbpF3julRJtaGpNHGkYsFM26bFRWbOsIgj3li2KcYrtcDM7CmHmdbB4RS+9vVKkMf/aIrwW5gzR03j3Y2Yj0gmc0bxhTEqr/8rS1nJn8RkwSsAqIpFGExh1CbSEX8cDNLGVG4QJVxK6wmTG4hKnYULMH7PPrFq/i9pvjEseAw5rifKZPsdrIhFrOtJeQ1KupI36zq6J4DH+sikSofyVZ1dkz1VnFO/O3eltFwHDl0r4oiEC/+3h+3L+DGPbtbmw9oVyz3IMrRJgRnTInt0+VwRQIlxuQS7Fd0B2/ydPpOyaGoovZtPUoTsKw4ut/sLyWlAY6oCh0GfJAMUCgcqabDfIpVlgV44u5KspRLGZ2jU8R7XMZvGcgLpy9WYq1TjolEoIFZOPF58avRi6E/2TAaBaiDTLOecDnEQBi0aPvHXpkFoEbL2n50AIydIVPaDHGy64UlB8Dr2tjPBcFI+QIROOveN9gmXeT+oTbSQ7WYfSyjQzcJD9jV/WSXWVzFjE1859zjzUsQXhKbRvZh/0ewLwrZBD9SkARpjjhCxqHhzfQK3jtkKA7Q76yRIaZ24gboeFQJxW6v+ntbEGCTMKbP7t5lEbgZwxFe168Or8009+PymsRAlL3nmls4Y27p5fA40Q08884gL6HN95O/3mt0rF0BZuIjCa8r5E3zbUL6muSRvGWi5epqfeOCPerDaJm4Yth9BWexQxEu6Hf43lDL2cjcciY7Drn/lvQeX0Ff0hHu7alL7ioeamZTOvxQpOVRqG9nIHur5HynpKbnFyvQxK1Asrph4V9wUQCGSrMq10EXi5wY5CA+7VOYEJKOZCt0xsAmpd5lyAxSltbS2IVnPUubgWuN5KmxuadyvGEXxTM4rfGlBp9kOcXY+QMfh7gcmEeq+Dr68n+RVi0ir1jrOLtmvWVZau8nizPBU0r0+gQvgQGC0Al2FTtybgH48ged\"\u003e \n        \u003cinput type\u003d\"hidden\" value\u003d\"4fe0d64d3699fcfda18b5510ced2ce96\" id\u003d\"jschl-vc\" name\u003d\"jschl_vc\"\u003e \n        \u003c!-- \u003cinput type\u003d\"hidden\" value\u003d\"\" id\u003d\"jschl-vc\" name\u003d\"jschl_vc\"/\u003e --\u003e \n        \u003cinput type\u003d\"hidden\" name\u003d\"pass\" value\u003d\"1614361051.011-ABabh7yVsU\"\u003e \n        \u003cinput type\u003d\"hidden\" id\u003d\"jschl-answer\" name\u003d\"jschl_answer\"\u003e \n       \u003c/form\u003e \n       \u003cscript type\u003d\"text/javascript\"\u003e\n      //\u003c![CDATA[\n      (function(){\n          var a \u003d document.getElementById(\u0027cf-content\u0027);\n          a.style.display \u003d \u0027block\u0027;\n          var isIE \u003d /(MSIE|Trident\\/|Edge\\/)/i.test(window.navigator.userAgent);\n          var trkjs \u003d isIE ? new Image() : document.createElement(\u0027img\u0027);\n          trkjs.setAttribute(\"src\", \"/cdn-cgi/images/trace/jschal/js/transparent.gif?ray\u003d627b6f1fc94f0cb5\");\n          trkjs.id \u003d \"trk_jschal_js\";\n          trkjs.setAttribute(\"alt\", \"\");\n          document.body.appendChild(trkjs);\n          var cpo\u003ddocument.createElement(\u0027script\u0027);\n          cpo.type\u003d\u0027text/javascript\u0027;\n          cpo.src\u003d\"/cdn-cgi/challenge-platform/h/g/orchestrate/jsch/v1\";\n          document.getElementsByTagName(\u0027head\u0027)[0].appendChild(cpo);\n        }());\n      //]]\u003e\n    \u003c/script\u003e \n       \u003cdiv id\u003d\"trk_jschal_nojs\" style\u003d\"background-image:url(\u0027/cdn-cgi/images/trace/jschal/nojs/transparent.gif?ray\u003d627b6f1fc94f0cb5\u0027)\"\u003e \n       \u003c/div\u003e \n      \u003c/div\u003e \n      \u003cdiv class\u003d\"attribution\"\u003e\n        DDoS protection by \n       \u003ca rel\u003d\"noopener noreferrer\" href\u003d\"https://www.cloudflare.com/5xx-error-landing/\" target\u003d\"_blank\"\u003eCloudflare\u003c/a\u003e \n       \u003cbr\u003e \n       \u003cspan class\u003d\"ray_id\"\u003eRay ID: \u003ccode\u003e627b6f1fc94f0cb5\u003c/code\u003e\u003c/span\u003e \n      \u003c/div\u003e \u003c/td\u003e \n    \u003c/tr\u003e \n   \u003c/tbody\u003e\n  \u003c/table\u003e   \n \u003c/body\u003e\n\u003c/html\u003e/","categories":[],"tags":"null"}"""
        "https://readcomicsonline.ru" -> """{"language":"en","name":"Read Comics Online","base_url":"https://readcomicsonline.ru","supports_latest":true,"isNsfw":false,"item_url":"https://readcomicsonline.ru/comic/","categories":[{"id":"1","name":"One Shots \u0026 TPBs"},{"id":"2","name":"DC Comics"},{"id":"3","name":"Marvel Comics"},{"id":"4","name":"Boom Studios"},{"id":"5","name":"Dynamite"},{"id":"6","name":"Rebellion"},{"id":"7","name":"Dark Horse"},{"id":"8","name":"IDW"},{"id":"9","name":"Archie"},{"id":"10","name":"Graphic India"},{"id":"11","name":"Darby Pop"},{"id":"12","name":"Oni Press"},{"id":"13","name":"Icon Comics"},{"id":"14","name":"United Plankton"},{"id":"15","name":"Udon"},{"id":"16","name":"Image Comics"},{"id":"17","name":"Valiant"},{"id":"18","name":"Vertigo"},{"id":"19","name":"Devils Due"},{"id":"20","name":"Aftershock Comics"},{"id":"21","name":"Antartic Press"},{"id":"22","name":"Action Lab"},{"id":"23","name":"American Mythology"},{"id":"24","name":"Zenescope"},{"id":"25","name":"Top Cow"},{"id":"26","name":"Hermes Press"},{"id":"27","name":"451"},{"id":"28","name":"Black Mask"},{"id":"29","name":"Chapterhouse Comics"},{"id":"30","name":"Red 5"},{"id":"31","name":"Heavy Metal"},{"id":"32","name":"Bongo"},{"id":"33","name":"Top Shelf"},{"id":"34","name":"Bubble"},{"id":"35","name":"Boundless"},{"id":"36","name":"Avatar Press"},{"id":"37","name":"Space Goat Productions"},{"id":"38","name":"BroadSword Comics"},{"id":"39","name":"AAM-Markosia"},{"id":"40","name":"Fantagraphics"},{"id":"41","name":"Aspen"},{"id":"42","name":"American Gothic Press"},{"id":"43","name":"Vault"},{"id":"44","name":"215 Ink"},{"id":"45","name":"Abstract Studio"},{"id":"46","name":"Albatross"},{"id":"47","name":"ARH Comix"},{"id":"48","name":"Legendary Comics"},{"id":"49","name":"Monkeybrain"},{"id":"50","name":"Joe Books"},{"id":"51","name":"MAD"},{"id":"52","name":"Comics Experience"},{"id":"53","name":"Alterna Comics"},{"id":"54","name":"Lion Forge"},{"id":"55","name":"Benitez"},{"id":"56","name":"Storm King"},{"id":"57","name":"Sucker"},{"id":"58","name":"Amryl Entertainment"},{"id":"59","name":"Ahoy Comics"},{"id":"60","name":"Mad Cave"},{"id":"61","name":"Coffin Comics"},{"id":"62","name":"Magnetic Press"},{"id":"63","name":"Ablaze"},{"id":"64","name":"Europe Comics"},{"id":"65","name":"Humanoids"},{"id":"66","name":"TKO"},{"id":"67","name":"Soleil"},{"id":"68","name":"SAF Comics"},{"id":"69","name":"Scholastic"},{"id":"70","name":"Upshot"},{"id":"71","name":"Stranger Comics"},{"id":"72","name":"Inverse"},{"id":"73","name":"Virus"}],"tags":"null"}"""
        "https://manga.fascans.com" -> """{"language":"en","name":"Fallen Angels","base_url":"https://manga.fascans.com","supports_latest":true,"isNsfw":false,"item_url":"https://manga.fascans.com/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"},{"id":"33","name":"4-Koma"},{"id":"34","name":"Cooking"}],"tags":"null"}"""
        "https://zahard.top" -> """{"language":"en","name":"Zahard","base_url":"https://zahard.top","supports_latest":true,"isNsfw":false,"item_url":"https://zahard.top/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":[{"id":"tag","name":"("},{"id":"sdgsdg","name":"sdgsdg"},{"id":"action","name":"Action"},{"id":"fantasy","name":"Fantasy"},{"id":"manhwa","name":"Manhwa"},{"id":"martial-arts","name":"Martial Arts"},{"id":"shounen","name":"Shounen"},{"id":"webtoon","name":"Webtoon"},{"id":"webtoon","name":"Webtoon"},{"id":"action","name":"Action"},{"id":"fantasy","name":"Fantasy"}]}"""
        "https://manhwas.men" -> """{"language":"en","name":"Manhwas Men","base_url":"https://manhwas.men","supports_latest":true,"isNsfw":false,"item_url":"https://manhwas.men/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":[{"id":"tag","name":"前女友变女佣"},{"id":"four-sisters","name":"Four sisters"},{"id":"in-laws","name":"in-laws"},{"id":"raws","name":"raws"},{"id":"adult","name":"Adult"},{"id":"raw","name":"RAW"},{"id":"drama","name":"Drama"},{"id":"romance","name":"Romance"},{"id":"manhwa","name":"Manhwa"},{"id":"mature","name":"Mature"},{"id":"sub-english","name":"Sub English"}]}"""
        "https://www.scan-fr.cc" -> """{"language":"fr","name":"Scan FR","base_url":"https://www.scan-fr.cc","supports_latest":true,"isNsfw":false,"item_url":"https://www.scan-fr.cc/manga/","categories":[{"id":"1","name":"Comedy"},{"id":"2","name":"Doujinshi"},{"id":"3","name":"Drama"},{"id":"4","name":"Ecchi"},{"id":"5","name":"Fantasy"},{"id":"6","name":"Gender Bender"},{"id":"7","name":"Josei"},{"id":"8","name":"Mature"},{"id":"9","name":"Mecha"},{"id":"10","name":"Mystery"},{"id":"11","name":"One Shot"},{"id":"12","name":"Psychological"},{"id":"13","name":"Romance"},{"id":"14","name":"School Life"},{"id":"15","name":"Sci-fi"},{"id":"16","name":"Seinen"},{"id":"17","name":"Shoujo"},{"id":"18","name":"Shoujo Ai"},{"id":"19","name":"Shounen"},{"id":"20","name":"Shounen Ai"},{"id":"21","name":"Slice of Life"},{"id":"22","name":"Sports"},{"id":"23","name":"Supernatural"},{"id":"24","name":"Tragedy"},{"id":"25","name":"Yaoi"},{"id":"26","name":"Yuri"},{"id":"27","name":"Comics"},{"id":"28","name":"Autre"},{"id":"29","name":"BD Occidentale"},{"id":"30","name":"Manhwa"},{"id":"31","name":"Action"},{"id":"32","name":"Aventure"}],"tags":"null"}"""
        "https://www.scan-vf.net" -> """{"language":"fr","name":"Scan VF","base_url":"https://www.scan-vf.net","supports_latest":true,"isNsfw":false,"item_url":"https://www.scan-vf.net/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "https://scan-op.cc" -> """{"language":"fr","name":"Scan OP","base_url":"https://scan-op.cc","supports_latest":true,"isNsfw":false,"item_url":"https://scan-op.cc/manga/","categories":[{"id":"1","name":"Comedy"},{"id":"2","name":"Doujinshi"},{"id":"3","name":"Drama"},{"id":"4","name":"Ecchi"},{"id":"5","name":"Fantasy"},{"id":"6","name":"Gender Bender"},{"id":"7","name":"Josei"},{"id":"8","name":"Mature"},{"id":"9","name":"Mecha"},{"id":"10","name":"Mystery"},{"id":"11","name":"One Shot"},{"id":"12","name":"Psychological"},{"id":"13","name":"Romance"},{"id":"14","name":"School Life"},{"id":"15","name":"Sci-fi"},{"id":"16","name":"Seinen"},{"id":"17","name":"Shoujo"},{"id":"18","name":"Shoujo Ai"},{"id":"19","name":"Shounen"},{"id":"20","name":"Shounen Ai"},{"id":"21","name":"Slice of Life"},{"id":"22","name":"Sports"},{"id":"23","name":"Supernatural"},{"id":"24","name":"Tragedy"},{"id":"25","name":"Yaoi"},{"id":"26","name":"Yuri"},{"id":"27","name":"Comics"},{"id":"28","name":"Autre"}],"tags":[{"id":"nouveau","name":"nouveau"}]}"""
        "https://www.komikid.com" -> """{"language":"id","name":"Komikid","base_url":"https://www.komikid.com","supports_latest":true,"isNsfw":false,"item_url":"https://www.komikid.com/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Fantasy"},{"id":"7","name":"Gender Bender"},{"id":"8","name":"Historical"},{"id":"9","name":"Horror"},{"id":"10","name":"Josei"},{"id":"11","name":"Martial Arts"},{"id":"12","name":"Mature"},{"id":"13","name":"Mecha"},{"id":"14","name":"Mystery"},{"id":"15","name":"One Shot"},{"id":"16","name":"Psychological"},{"id":"17","name":"Romance"},{"id":"18","name":"School Life"},{"id":"19","name":"Sci-fi"},{"id":"20","name":"Seinen"},{"id":"21","name":"Shoujo"},{"id":"22","name":"Shoujo Ai"},{"id":"23","name":"Shounen"},{"id":"24","name":"Shounen Ai"},{"id":"25","name":"Slice of Life"},{"id":"26","name":"Sports"},{"id":"27","name":"Supernatural"},{"id":"28","name":"Tragedy"},{"id":"29","name":"Yaoi"},{"id":"30","name":"Yuri"}],"tags":"null"}"""
        "http://azbivo.webd.pro" -> """{"language":"pl","name":"Nikushima","base_url":"http://azbivo.webd.pro","supports_latest":true,"isNsfw":false,"item_url":"http://azbivo.webd.pro/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shounen"},{"id":"25","name":"Slice of Life"},{"id":"26","name":"Sports"},{"id":"27","name":"Supernatural"},{"id":"28","name":"Tragedy"},{"id":"29","name":"Isekai"}],"tags":"null"}"""
        "http://mangahanta.com" -> """{"language":"tr","name":"MangaHanta","base_url":"http://mangahanta.com","supports_latest":true,"isNsfw":false,"item_url":"http://mangahanta.com/manga/","categories":[{"id":"1","name":"Aksiyon"},{"id":"2","name":"Macera"},{"id":"3","name":"Komedi"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantezi"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Tarihi"},{"id":"11","name":"Korku"},{"id":"12","name":"Josei"},{"id":"13","name":"Dövüş Sanatları"},{"id":"14","name":"Yetişkin"},{"id":"15","name":"Mecha"},{"id":"16","name":"Gizem"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psikolojik"},{"id":"19","name":"Romantizm"},{"id":"20","name":"Okul Hayatı"},{"id":"21","name":"Bilim-Kurgu"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Hayattan Bir Parça"},{"id":"28","name":"Spor"},{"id":"29","name":"Doğaüstü"},{"id":"30","name":"Trajedi"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"},{"id":"33","name":"Vampir"},{"id":"34","name":"Webtoon"}],"tags":[{"id":"tag","name":"-ヒトガタナ-"},{"id":"amber","name":"Amber"},{"id":"amber-manga","name":"Amber manga"},{"id":"amber-oku","name":"Amber oku"},{"id":"amber-turkce-oku","name":"Amber Türkçe Oku"},{"id":"amber-yuno","name":"Amber Yuno"},{"id":"back-stage","name":"Back Stage"},{"id":"ballroom-e-youkoso","name":"Ballroom e Youkoso"},{"id":"beauty-game","name":"Beauty Game"},{"id":"beauty-game-oku","name":"Beauty Game Oku"},{"id":"boku-wa-mari-no-naka","name":"Boku Wa Mari No Naka"},{"id":"god-eater-kyuuseishu-no-kikan","name":"God Eater - Kyuuseishu no Kikan"},{"id":"god-eater-the-spiral-fate","name":"God Eater - The Spiral Fate"},{"id":"happiness","name":"Happiness"},{"id":"happiness-manga-oku","name":"happiness manga oku"},{"id":"happiness-turkce-oku","name":"happiness türkçe oku"},{"id":"hitogatana","name":"Hitogatana"},{"id":"im-in-mari-im-inside-mari","name":"ぼくは麻理のなか I\u0027m in Mari I\u0027m Inside Mari"},{"id":"itsuwaribito-utsuho","name":"Itsuwaribito Utsuho"},{"id":"kaguya-sama-wa-kokurasetai","name":"Kaguya-sama wa Kokurasetai"},{"id":"les-memoires-de-vanitas","name":"Les Mémoires de Vanitas"},{"id":"mahouka-koukou-no-rettousei-tsuioku-hen","name":"Mahouka Koukou no Rettousei - Tsuioku Hen"},{"id":"manga-oku","name":"manga oku"},{"id":"maou-na-ore-to-fushihime-no-yubiwa","name":"Maou na Ore to Fushihime no Yubiwa"},{"id":"may-i-shake-your-hand","name":"May I shake your hand"},{"id":"may-i-shake-your-hand-oku","name":"may I shake your hand oku"},{"id":"may-i-shake-your-hand-turkce-oku","name":"May I Shake Your Hand türkçe oku"},{"id":"memoir-of-vanitas","name":"Memoir of Vanitas"},{"id":"mutluluk","name":"Mutluluk"},{"id":"nanatsu-no-taizai","name":"Nanatsu No Taizai"},{"id":"nanatsu-no-taizai-turkce-oku","name":"Nanatsu no taizai Türkçe oku"},{"id":"oshimi-shuzo","name":"OSHIMI Shuzo"},{"id":"sousei-manga-oku","name":"sousei manga oku"},{"id":"sousei-no-onmyouji","name":"Sousei no Onmyouji"},{"id":"sousei-no-onmyouji-manga-oku","name":"Sousei no onmyouji manga oku"},{"id":"sousei-no-onmyouji-turkce-oku","name":"sousei no onmyouji türkçe oku"},{"id":"the-case-study-of-vanitas","name":"The Case Study of Vanitas"},{"id":"the-seven-deadly-sins","name":"The Seven Deadly Sins"},{"id":"vanitas-no-carte","name":"Vanitas no Carte"},{"id":"vanitas-no-shuki","name":"Vanitas no Shuki"},{"id":"yedi-olumcul-gunah","name":"Yedi Ölümcül Günah"}]}"""
        "https://truyen.fascans.com" -> """{"language":"vi","name":"Fallen Angels Scans","base_url":"https://truyen.fascans.com","supports_latest":true,"isNsfw":false,"item_url":"https://truyen.fascans.com/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "https://leomanga.me" -> """{"language":"es","name":"LeoManga","base_url":"https://leomanga.me","supports_latest":false,"isNsfw":false,"item_url":"https://leomanga.me/manga/","categories":[{"id":"1","name":"Accion"},{"id":"2","name":"Aventura"},{"id":"3","name":"Comedia"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasia"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historico"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Artes Marciales"},{"id":"14","name":"Madura"},{"id":"15","name":"Mecha"},{"id":"16","name":"Misterio"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psicológico"},{"id":"19","name":"Romance"},{"id":"20","name":"Vida Cotidiana"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Supernatural"},{"id":"29","name":"Tragedia"},{"id":"30","name":"Yaoi"},{"id":"31","name":"Yuri"},{"id":"32","name":"Deporte"},{"id":"33","name":"Thriller"},{"id":"34","name":"Vida Escolar"},{"id":"35","name":"Boys Love"},{"id":"36","name":"Girls Love"},{"id":"37","name":"Gore"},{"id":"38","name":"Hentai"},{"id":"39","name":"Magia"},{"id":"40","name":"Manwha"},{"id":"41","name":"Policial"},{"id":"42","name":"Realidad Virtual"},{"id":"43","name":"Super Poderes"},{"id":"44","name":"Suspense"},{"id":"45","name":"Supervivencia"},{"id":"46","name":"Parodia"},{"id":"47","name":"Demonios"},{"id":"48","name":"Escolar"}],"tags":[{"id":"freaking-romance","name":"Freaking Romance"},{"id":"love-lucky","name":"Love Lucky"},{"id":"lust-awakening","name":"Lust Awakening"},{"id":"despertar-de-la-lujuria","name":"Despertar de la lujuria"},{"id":"inazumaelevenaresnotenbin","name":"inazumaelevenaresnotenbin"},{"id":"heir-of-the-penguins","name":"Heir of the Penguins"},{"id":"amor","name":"amor"},{"id":"drama","name":"drama"},{"id":"mysteries","name":"mysteries"},{"id":"anal","name":"anal"},{"id":"bukkake","name":"bukkake"},{"id":"doble-penetracion","name":"doble penetracion"},{"id":"orgia","name":"orgia"},{"id":"blow-job","name":"blow job"},{"id":"big-breasts","name":"big breasts"},{"id":"incesto","name":"incesto"},{"id":"milf","name":"milf"},{"id":"prenadas","name":"preñadas"},{"id":"slave-sex","name":"slave sex"},{"id":"lolicon","name":"lolicon"},{"id":"nurse","name":"nurse"},{"id":"reality","name":"Reality"},{"id":"glitch","name":"Glitch"},{"id":"glitcher","name":"Glitcher"},{"id":"horror","name":"Horror"},{"id":"suspenso","name":"Suspenso"},{"id":"realidad","name":"Realidad"},{"id":"slider","name":"Slider"},{"id":"novela","name":"Novela"},{"id":"sobrenatural","name":"Sobrenatural"},{"id":"tragedia","name":"Tragedia"},{"id":"error","name":"Error"},{"id":"psicologico","name":"Psicologico"},{"id":"sufrimiento","name":"Sufrimiento"},{"id":"visual","name":"Visual"},{"id":"narrativo","name":"Narrativo"},{"id":"shotacon","name":"shotacon"},{"id":"paizuri","name":"paizuri"},{"id":"kemonomimi","name":"kemonomimi"},{"id":"mundo-paralelo","name":"mundo paralelo"},{"id":"coleccion-hentai","name":"coleccion hentai"},{"id":"adultos","name":"adultos"}]}"""
        "https://submanga.io" -> """{"language":"es","name":"submanga","base_url":"https://submanga.io","supports_latest":false,"isNsfw":false,"item_url":"https://submanga.io/manga/","categories":[{"id":"1","name":"Accion"},{"id":"2","name":"Aventura"},{"id":"3","name":"Comedia"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasia"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historico"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Artes Marciales"},{"id":"14","name":"Madura"},{"id":"15","name":"Mecha"},{"id":"16","name":"Misterio"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psicológico"},{"id":"19","name":"Romance"},{"id":"20","name":"Vida Cotidiana"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Supernatural"},{"id":"29","name":"Tragedia"},{"id":"30","name":"Yaoi"},{"id":"31","name":"Yuri"},{"id":"32","name":"Deporte"},{"id":"33","name":"Thriller"},{"id":"34","name":"Vida Escolar"},{"id":"35","name":"Boys Love"},{"id":"36","name":"Girls Love"},{"id":"37","name":"Gore"},{"id":"38","name":"Hentai"},{"id":"39","name":"Magia"},{"id":"40","name":"Manwha"},{"id":"41","name":"Policial"},{"id":"42","name":"Realidad Virtual"},{"id":"43","name":"Super Poderes"},{"id":"44","name":"Suspense"},{"id":"45","name":"Supervivencia"},{"id":"46","name":"Parodia"},{"id":"47","name":"Demonios"},{"id":"48","name":"Escolar"}],"tags":"null"}"""
        "https://mangas.in" -> """{"language":"es","name":"Mangas.pw","base_url":"https://mangas.in","supports_latest":true,"isNsfw":false,"item_url":"https://mangas.in/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"},{"id":"33","name":"Hentai"},{"id":"34","name":"Smut"}],"tags":"null"}"""
        "https://manga.utsukushii-bg.com" -> """{"language":"bg","name":"Utsukushii","base_url":"https://manga.utsukushii-bg.com","supports_latest":true,"isNsfw":false,"item_url":"https://manga.utsukushii-bg.com/manga/","categories":[{"id":"1","name":"Екшън"},{"id":"2","name":"Приключенски"},{"id":"3","name":"Комедия"},{"id":"4","name":"Драма"},{"id":"5","name":"Фентъзи"},{"id":"6","name":"Исторически"},{"id":"7","name":"Ужаси"},{"id":"8","name":"Джосей"},{"id":"9","name":"Бойни изкуства"},{"id":"10","name":"Меха"},{"id":"11","name":"Мистерия"},{"id":"12","name":"Самостоятелна/Пилотна глава"},{"id":"13","name":"Психологически"},{"id":"14","name":"Романтика"},{"id":"15","name":"Училищни"},{"id":"16","name":"Научна фантастика"},{"id":"17","name":"Сейнен"},{"id":"18","name":"Шоджо"},{"id":"19","name":"Реализъм"},{"id":"20","name":"Спорт"},{"id":"21","name":"Свръхестествено"},{"id":"22","name":"Трагедия"},{"id":"23","name":"Йокаи"},{"id":"24","name":"Паралелна вселена"},{"id":"25","name":"Супер сили"},{"id":"26","name":"Пародия"},{"id":"27","name":"Шонен"}],"tags":"null"}"""
        "https://phoenix-scans.pl" -> """{"language":"pl","name":"Phoenix-Scans","base_url":"https://phoenix-scans.pl","supports_latest":true,"isNsfw":false,"item_url":"https://phoenix-scans.pl/manga/","categories":[{"id":"1","name":"Shounen"},{"id":"2","name":"Tragedia"},{"id":"3","name":"Szkolne życie"},{"id":"4","name":"Romans"},{"id":"5","name":"Zagadka"},{"id":"6","name":"Horror"},{"id":"7","name":"Dojrzałe"},{"id":"8","name":"Psychologiczne"},{"id":"9","name":"Przygodowe"},{"id":"10","name":"Akcja"},{"id":"11","name":"Komedia"},{"id":"12","name":"Zboczone"},{"id":"13","name":"Fantasy"},{"id":"14","name":"Harem"},{"id":"15","name":"Historyczne"},{"id":"16","name":"Manhua"},{"id":"17","name":"Manhwa"},{"id":"18","name":"Sztuki walki"},{"id":"19","name":"One shot"},{"id":"20","name":"Sci fi"},{"id":"21","name":"Seinen"},{"id":"22","name":"Shounen ai"},{"id":"23","name":"Spokojne życie"},{"id":"24","name":"Sport"},{"id":"25","name":"Nadprzyrodzone"},{"id":"26","name":"Webtoons"},{"id":"27","name":"Dramat"},{"id":"28","name":"Hentai"},{"id":"29","name":"Mecha"},{"id":"30","name":"Gender Bender"},{"id":"31","name":"Gry"},{"id":"32","name":"Yaoi"}],"tags":[{"id":"aktywne","name":"aktywne"},{"id":"zakonczone","name":"zakończone"},{"id":"porzucone","name":"porzucone"}]}"""
        "https://puzzmos.com" -> """{"language":"tr","name":"Puzzmos","base_url":"https://puzzmos.com","supports_latest":true,"isNsfw":false,"item_url":"https://puzzmos.com/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":[{"id":"2011","name":"2011"}]}"""
        "https://wwv.scan-1.com" -> """{"language":"fr","name":"Scan-1","base_url":"https://wwv.scan-1.com","supports_latest":true,"isNsfw":false,"item_url":"https://wwv.scan-1.com/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "https://www.lelscan-vf.com" -> """{"language":"fr","name":"Lelscan-VF","base_url":"https://www.lelscan-vf.com","supports_latest":true,"isNsfw":false,"item_url":"https://lelscan-vf.co/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "https://adm.komikmanga.com" -> """{"language":"id","name":"Komik Manga","base_url":"https://adm.komikmanga.com","supports_latest":true,"isNsfw":false,"item_url":"https://adm.komikmanga.com/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"},{"id":"33","name":"Adult"},{"id":"34","name":"Isekai"}],"tags":"null"}"""
        "https://raws.mangazuki.co" -> """{"language":"ko","name":"Mangazuki Raws","base_url":"https://raws.mangazuki.co","supports_latest":false,"isNsfw":false,"item_url":"\u003c!doctype html\u003e\n\u003chtml lang\u003d\"en-US\"\u003e\n \u003chead\u003e \n  \u003cmeta charset\u003d\"UTF-8\"\u003e \n  \u003cmeta http-equiv\u003d\"Content-Type\" content\u003d\"text/html; charset\u003dUTF-8\"\u003e \n  \u003cmeta http-equiv\u003d\"X-UA-Compatible\" content\u003d\"IE\u003dEdge,chrome\u003d1\"\u003e \n  \u003cmeta name\u003d\"robots\" content\u003d\"noindex, nofollow\"\u003e \n  \u003cmeta name\u003d\"viewport\" content\u003d\"width\u003ddevice-width,initial-scale\u003d1\"\u003e \n  \u003ctitle\u003eJust a moment...\u003c/title\u003e \n  \u003cstyle type\u003d\"text/css\"\u003e\n    html, body {width: 100%; height: 100%; margin: 0; padding: 0;}\n    body {background-color: #ffffff; color: #000000; font-family:-apple-system, system-ui, BlinkMacSystemFont, \"Segoe UI\", Roboto, Oxygen, Ubuntu, \"Helvetica Neue\",Arial, sans-serif; font-size: 16px; line-height: 1.7em;-webkit-font-smoothing: antialiased;}\n    h1 { text-align: center; font-weight:700; margin: 16px 0; font-size: 32px; color:#000000; line-height: 1.25;}\n    p {font-size: 20px; font-weight: 400; margin: 8px 0;}\n    p, .attribution, {text-align: center;}\n    #spinner {margin: 0 auto 30px auto; display: block;}\n    .attribution {margin-top: 32px;}\n    @keyframes fader     { 0% {opacity: 0.2;} 50% {opacity: 1.0;} 100% {opacity: 0.2;} }\n    @-webkit-keyframes fader { 0% {opacity: 0.2;} 50% {opacity: 1.0;} 100% {opacity: 0.2;} }\n    #cf-bubbles \u003e .bubbles { animation: fader 1.6s infinite;}\n    #cf-bubbles \u003e .bubbles:nth-child(2) { animation-delay: .2s;}\n    #cf-bubbles \u003e .bubbles:nth-child(3) { animation-delay: .4s;}\n    .bubbles { background-color: #f58220; width:20px; height: 20px; margin:2px; border-radius:100%; display:inline-block; }\n    a { color: #2c7cb0; text-decoration: none; -moz-transition: color 0.15s ease; -o-transition: color 0.15s ease; -webkit-transition: color 0.15s ease; transition: color 0.15s ease; }\n    a:hover{color: #f4a15d}\n    .attribution{font-size: 16px; line-height: 1.5;}\n    .ray_id{display: block; margin-top: 8px;}\n    #cf-wrapper #challenge-form { padding-top:25px; padding-bottom:25px; }\n    #cf-hcaptcha-container { text-align:center;}\n    #cf-hcaptcha-container iframe { display: inline-block;}\n  \u003c/style\u003e \n  \u003cmeta http-equiv\u003d\"refresh\" content\u003d\"12\"\u003e \n  \u003cscript type\u003d\"text/javascript\"\u003e\n    //\u003c![CDATA[\n    (function(){\n      \n      window._cf_chl_opt\u003d{\n        cvId: \"2\",\n        cType: \"non-interactive\",\n        cNounce: \"40325\",\n        cRay: \"627b711a599ff039\",\n        cHash: \"5147677fb1f5d40\",\n        cFPWv: \"g\",\n        cTTimeMs: \"4000\",\n        cRq: {\n          ru: \"aHR0cHM6Ly9yYXdzLm1hbmdhenVraS5jby8\u003d\",\n          ra: \"b2todHRwLzMuMTAuMA\u003d\u003d\",\n          rm: \"R0VU\",\n          d: \"zOGkQGkYxYN36DBxdP1ySsz6tiLlkQkBxrGzqnIu8m7tz0oU3UVPcErPDphWYj5WNqtHEHwzhlsJKUSLzNPyF1yInxlQBEYRqsbZbWYnfRVmXQTZPnG6KBJ4fFYhX4YR8HXuSDwgJ6kngCW+ekC7vZsh2dnmUmvO6JN4PuTOyC97VAERuALWj/GlkDBQ1FQXR/wEWAqf3V6cCdlZcKXkD5UbRZzhxlw7EsTMnF01amXUMtLV6ggBEcriXWDkFTbFUZpQSUZjm0pwykFJOaXFZwvAaIA7PjQK2uMyTzv6QwZfL5OKpcXOguhdtUigVrQyNHEG1s1gHte0zPG1xGma6LWOrHYatLP5JW4mVJykht+HwwJ04RLICu1f0bJ6SM/yTBWHpnfZrs6hV05dlOmemvE9MQBytWhOi9lGKaVmYxmbgEY0alUGardqWWCxISXYPDhe4+Y0Rxpb6kX+lIiTtqiVSQ5PxiRy8lgQYV2Pp+qWJbe1iHJzMaqFpgJvI0ihjdKXl4KsmxsYZi8QhlDYJcTZyknVI0LHHdAGZUJUnjKGL23ec2f+nLxGn1ZECeib\",\n          t: \"MTYxNDM2MTEyOC4wNjYwMDA\u003d\",\n          m: \"pX0OPLFnDvuS66xf7tMswlvFBJ7CvM2jGx9C5ihYGOE\u003d\",\n          i1: \"g+cJjv/W9/ggN52ebrJZSg\u003d\u003d\",\n          i2: \"luhLCMllcfCNJTIko98vrQ\u003d\u003d\",\n          uh: \"QNqr1PtsmAsBuHIaoM6zeJRgUdRT1sK83/SuOuA+LQM\u003d\",\n          hh: \"I0kdqj2F0l7JNXvXS7ighNXMGXUM2prtK7PBi3zI0Kw\u003d\",\n        }\n      }\n      window._cf_chl_enter \u003d function(){window._cf_chl_opt.p\u003d1};\n      \n    })();\n    //]]\u003e\n  \u003c/script\u003e \n \u003c/head\u003e \n \u003cbody\u003e \n  \u003ctable width\u003d\"100%\" height\u003d\"100%\" cellpadding\u003d\"20\"\u003e \n   \u003ctbody\u003e\n    \u003ctr\u003e \n     \u003ctd align\u003d\"center\" valign\u003d\"middle\"\u003e \n      \u003cdiv class\u003d\"cf-browser-verification cf-im-under-attack\"\u003e \n       \u003cnoscript\u003e \n        \u003ch1 data-translate\u003d\"turn_on_js\" style\u003d\"color:#bd2426;\"\u003ePlease turn JavaScript on and reload the page.\u003c/h1\u003e \n       \u003c/noscript\u003e \n       \u003cdiv id\u003d\"cf-content\" style\u003d\"display:none\"\u003e \n        \u003cdiv id\u003d\"cf-bubbles\"\u003e \n         \u003cdiv class\u003d\"bubbles\"\u003e\u003c/div\u003e \n         \u003cdiv class\u003d\"bubbles\"\u003e\u003c/div\u003e \n         \u003cdiv class\u003d\"bubbles\"\u003e\u003c/div\u003e \n        \u003c/div\u003e \n        \u003ch1\u003e\u003cspan data-translate\u003d\"checking_browser\"\u003eChecking your browser before accessing\u003c/span\u003e mangazuki.co.\u003c/h1\u003e \n        \u003cdiv id\u003d\"no-cookie-warning\" class\u003d\"cookie-warning\" data-translate\u003d\"turn_on_cookies\" style\u003d\"display:none\"\u003e \n         \u003cp data-translate\u003d\"turn_on_cookies\" style\u003d\"color:#bd2426;\"\u003ePlease enable Cookies and reload the page.\u003c/p\u003e \n        \u003c/div\u003e \n        \u003cp data-translate\u003d\"process_is_automatic\"\u003eThis process is automatic. Your browser will redirect to your requested content shortly.\u003c/p\u003e \n        \u003cp data-translate\u003d\"allow_5_secs\" id\u003d\"cf-spinner-allow-5-secs\"\u003ePlease allow up to 5 seconds…\u003c/p\u003e \n        \u003cp data-translate\u003d\"redirecting\" id\u003d\"cf-spinner-redirecting\" style\u003d\"display:none\"\u003eRedirecting…\u003c/p\u003e \n       \u003c/div\u003e \n       \u003cform class\u003d\"challenge-form\" id\u003d\"challenge-form\" action\u003d\"/?__cf_chl_jschl_tk__\u003dc8ab60c47f906f94060319e5f52de899835eec8e-1614361128-0-AfLLJ88dk7y3cdA18ZmVK0qTd-Bjlb02LGoF7HoTOiYr1awxAMGzNap9BOcbQpDdyPIDcoKeMdqBULq_vLrG9wfL6LuiLweId9-hmMiFoA9BwSHR2sQk35BV6ZKAUGfnf4eWZ-aM78rAExX-29lgmjyGaCXSeKR_-VomlyUrQwt40_7MfrkyDxpzQw8abDIoy3ujNZJbhSTt0fBkvJapIMh26HzivQae9MkvrVuiL3U7RwY3261s-HzBjUfXXIcd9X1e1TiZqmc6-2HTHGCLvXPo--ywNyExoRrZmycRWVLM2xUXJtz1WSG_YWAHY28N1w\" method\u003d\"POST\" enctype\u003d\"application/x-www-form-urlencoded\"\u003e \n        \u003cinput type\u003d\"hidden\" name\u003d\"r\" value\u003d\"946bc6f89d52ba0964e58e13a060262cc557b8bd-1614361128-0-AdebX22CmfOqZtfz6VfyprBwYzRQ+3Q4uck0LMh6cBBugBO9umDRhN/aSd2+4TEPN3+QP5/2zRLKckLh17N+YjHg0CV19AWqtaHEYhhrzQD9sye8brVMa2HLuRaq3TO3bhWWj+hY7bQu09zCIIxbKpW/JYNaMUA05QGyfNWOV4W/zt1KOPDzxj+iRD0vWQNnK2GQSFYlh5f2/WLVclTEQIqliSGTotnFuIi1MU9zaYfuX1Ol2WPuAwXlJoIfjRC/oNI3M7GVtRXDsKWkSea+3g3wQn2pB3oXX49saJ0s2J6uejnI741ifuZjp5LuWp4ZFst0bCUZPJO8jI/0Stkg/Xd9mEba43JqS+GKNq9npOkuiagZc+lURzLz2ECDtU0x+JWvOgZ2xSapT4ufdKrgVe0q1Bp8xm1QIvGQLFzGjDJtuTH7OcgoQ6gYV8/ty/6wUgrXfPzK47W0iIDrWjOmiN7Y/Gt1lBtj+8oQgzR6ABNZlLOf2IQ7Eauns+MX+4ks3vIK7WRM8KmgkGUcgB2FIwJSJJ573OjWqZkQQkPhEV2yjhIjT8tOzGX9C/nwIHF4i6vpvwSVRCtLjQx+OBdY6M0f+4fcB4TnVW7rR9R8mZ4+bGcCxJ4bNroadBAvkLcbaWJYod8pFdEa8iOHJETK4aAsg1zMixTU89Fj1zeE8JatJ8Je/m1D6fiLiO1KBkeLB3ov0uBUPVSH9R+DzxYOSbbu+LMt97QndIXBoarS2K72zemqjKbHYkTFtWuKvEbnELbOM8993H7SvtUHUKgiTaF+mWVTd1Rv4k8hywCu4cGOupiFUuif14PmEWFrFec0rVlKRDZWf60c/RJ7s/dSd0ZvZO+S2yom9C19EtZuTfnAiX8vYG84O9MsfOM/l+JtvY7yx0lgl7vj7/Genx6l4tOFxulO/Pvy0ZEnlWqNW5EJC491tqAL51C9btiGC5PHOCa6+tKcq2s5hcujAZzqm366hbNcyR8aVQSjY9cdmKImAilaNNiOdLP3Ci2Y13w5tIsO6ifRa+0LSyLPwwlZdEhQ16bWw8ObWrKel5aoJF2Jb2Dk71vaoVIBTCNuOeXdUtJsk1CEk6btguF+2bTj8TClicYRUGxxQM7GGGnIAAm85zmnrAQYsm701FN6SR2XnBpUjDjE5fRGR4EgBQ7LS4QT6tRmcN77FkKQUkiePR2Wq59rhPa6jXSthXPjPAB9FISMyJjuVazstV1taFUVzAp/A/CK2i0Ti6yaacu7sSxnbHykyw/kg87FYNTYw9gpSz0ICNJNanHX1+/vFGvq/cVh9syZVZjTQfPNoT61uQk677awNU5zTmAP/M9KKtMCxNTEezVSh3KrL9MCK5FDiWn8H/FFSRFTj6WyWZFoz0/r3VSQjcRdbw7soTy8wahb3Beko0KLPi802q76s74WUUM\u003d\"\u003e \n        \u003cinput type\u003d\"hidden\" value\u003d\"e1ae30b322b2f3884861a95378ea1e08\" id\u003d\"jschl-vc\" name\u003d\"jschl_vc\"\u003e \n        \u003c!-- \u003cinput type\u003d\"hidden\" value\u003d\"\" id\u003d\"jschl-vc\" name\u003d\"jschl_vc\"/\u003e --\u003e \n        \u003cinput type\u003d\"hidden\" name\u003d\"pass\" value\u003d\"1614361132.066-Lv6AWAuxFD\"\u003e \n        \u003cinput type\u003d\"hidden\" id\u003d\"jschl-answer\" name\u003d\"jschl_answer\"\u003e \n       \u003c/form\u003e \n       \u003cscript type\u003d\"text/javascript\"\u003e\n      //\u003c![CDATA[\n      (function(){\n          var a \u003d document.getElementById(\u0027cf-content\u0027);\n          a.style.display \u003d \u0027block\u0027;\n          var isIE \u003d /(MSIE|Trident\\/|Edge\\/)/i.test(window.navigator.userAgent);\n          var trkjs \u003d isIE ? new Image() : document.createElement(\u0027img\u0027);\n          trkjs.setAttribute(\"src\", \"/cdn-cgi/images/trace/jschal/js/transparent.gif?ray\u003d627b711a599ff039\");\n          trkjs.id \u003d \"trk_jschal_js\";\n          trkjs.setAttribute(\"alt\", \"\");\n          document.body.appendChild(trkjs);\n          var cpo\u003ddocument.createElement(\u0027script\u0027);\n          cpo.type\u003d\u0027text/javascript\u0027;\n          cpo.src\u003d\"/cdn-cgi/challenge-platform/h/g/orchestrate/jsch/v1\";\n          document.getElementsByTagName(\u0027head\u0027)[0].appendChild(cpo);\n        }());\n      //]]\u003e\n    \u003c/script\u003e \n       \u003cdiv id\u003d\"trk_jschal_nojs\" style\u003d\"background-image:url(\u0027/cdn-cgi/images/trace/jschal/nojs/transparent.gif?ray\u003d627b711a599ff039\u0027)\"\u003e \n       \u003c/div\u003e \n      \u003c/div\u003e \n      \u003cdiv class\u003d\"attribution\"\u003e\n        DDoS protection by \n       \u003ca rel\u003d\"noopener noreferrer\" href\u003d\"https://www.cloudflare.com/5xx-error-landing/\" target\u003d\"_blank\"\u003eCloudflare\u003c/a\u003e \n       \u003cbr\u003e \n       \u003cspan class\u003d\"ray_id\"\u003eRay ID: \u003ccode\u003e627b711a599ff039\u003c/code\u003e\u003c/span\u003e \n      \u003c/div\u003e \u003c/td\u003e \n    \u003c/tr\u003e \n   \u003c/tbody\u003e\n  \u003c/table\u003e   \n \u003c/body\u003e\n\u003c/html\u003e/","categories":[],"tags":"null"}"""
        "https://remangas.top" -> """{"language":"pt-BR","name":"Remangas","base_url":"https://remangas.top","supports_latest":true,"isNsfw":false,"item_url":"https://remangas.top/manga/","categories":[{"id":"1","name":"Ação"},{"id":"2","name":"Aventura"},{"id":"3","name":"Comédia"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasia"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Histórico"},{"id":"11","name":"Terror"},{"id":"12","name":"Josei"},{"id":"13","name":"Artes Marciais"},{"id":"14","name":"Adulto"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mistério"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psicológico"},{"id":"19","name":"Romance"},{"id":"20","name":"Vida escolar"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Esporte"},{"id":"29","name":"Sobrenatural"},{"id":"30","name":"Tragédia"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"},{"id":"33","name":"Isekai"},{"id":"34","name":"Guerra"},{"id":"35","name":"Sobrevivência"},{"id":"36","name":"Romance?"}],"tags":[{"id":"seinen","name":"seinen"},{"id":"ecchi","name":"ecchi"},{"id":"harem","name":"harem"},{"id":"isekai","name":"isekai"},{"id":"guerra","name":"guerra"},{"id":"shounen","name":"shounen"},{"id":"18","name":"+18"},{"id":"adulto","name":"Adulto"},{"id":"fantasia","name":"Fantasia"},{"id":"romance","name":"Romance"},{"id":"vida-escolar","name":"Vida Escolar"},{"id":"acao","name":"Ação"},{"id":"misterio","name":"mistério"},{"id":"terror","name":"Terror"},{"id":"detetive","name":"Detetive"},{"id":"misterios","name":"Mistérios"},{"id":"incesto","name":"Incesto"},{"id":"comedia-romantica","name":"Comédia Romantica"},{"id":"alquimia","name":"Alquimia"},{"id":"manhua","name":"Manhua"},{"id":"colorido","name":"Colorido"},{"id":"antologia","name":"Antologia"},{"id":"dragoes","name":"Dragões"},{"id":"briga-de-rua","name":"Briga de Rua"},{"id":"anti-heroi","name":"Anti Herói"},{"id":"zoera","name":"Zoera"},{"id":"protagonista-overpower","name":"Protagonista Overpower"},{"id":"psicologico","name":"Psicológico"},{"id":"protagonista-badass","name":"Protagonista Badass"},{"id":"battleroyale","name":"Battleroyale"},{"id":"apocalispe-zumbi","name":"Apocalispe Zumbi"},{"id":"mc-nao-virjao","name":"Mc Não Virjão"},{"id":"escola-de-magia","name":"Escola de Magia"},{"id":"tensei","name":"Tensei"},{"id":"shota-badass","name":"Shota Badass"},{"id":"isekai-vai-e-volta","name":"Isekai Vai e Volta"},{"id":"gore","name":"gore"},{"id":"garota-monstro","name":"Garota Monstro"},{"id":"maid","name":"Maid"},{"id":"gal","name":"Gal"},{"id":"mordomo","name":"Mordomo"}]}"""
        "https://animaregia.net" -> """{"language":"pt-BR","name":"AnimaRegia","base_url":"https://animaregia.net","supports_latest":true,"isNsfw":false,"item_url":"http://animaregia.net/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "http://manga-v2.mangavadisi.org" -> """{"language":"tr","name":"MangaVadisi","base_url":"http://manga-v2.mangavadisi.org","supports_latest":true,"isNsfw":false,"item_url":"http://manga-v2.mangavadisi.org/manga/","categories":[{"id":"1","name":"Aksiyon"},{"id":"2","name":"Macera"},{"id":"3","name":"Komedi"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantastik"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Tarihi"},{"id":"11","name":"Korku"},{"id":"12","name":"Josei"},{"id":"13","name":"Dövüş Sanatları"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Gizem"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psikolojik"},{"id":"19","name":"Romantizm"},{"id":"20","name":"Okul Hayatı"},{"id":"21","name":"Bilim Kurgu"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Günlük Yaşam"},{"id":"28","name":"Spor"},{"id":"29","name":"Doğaüstü"},{"id":"30","name":"Trajedi"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "https://mangaid.click" -> """{"language":"id","name":"MangaID","base_url":"https://mangaid.click","supports_latest":true,"isNsfw":false,"item_url":"https://mangaid.click/manga/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"Psychological"},{"id":"18","name":"Romance"},{"id":"19","name":"School Life"},{"id":"20","name":"Sci-fi"},{"id":"21","name":"Seinen"},{"id":"22","name":"Shoujo"},{"id":"23","name":"Shoujo Ai"},{"id":"24","name":"Shounen"},{"id":"25","name":"Shounen Ai"},{"id":"26","name":"Slice of Life"},{"id":"27","name":"Sports"},{"id":"28","name":"Supernatural"},{"id":"29","name":"Tragedy"},{"id":"30","name":"Yaoi"},{"id":"31","name":"Yuri"},{"id":"32","name":"School"},{"id":"33","name":"Isekai"},{"id":"34","name":"Military"}],"tags":"null"}"""
        "https://jpmangas.co" -> """{"language":"fr","name":"Jpmangas","base_url":"https://jpmangas.co","supports_latest":true,"isNsfw":false,"item_url":"https://jpmangas.co/lecture-en-ligne/","categories":[{"id":"1","name":"Action"},{"id":"2","name":"Adventure"},{"id":"3","name":"Comedy"},{"id":"4","name":"Doujinshi"},{"id":"5","name":"Drama"},{"id":"6","name":"Ecchi"},{"id":"7","name":"Fantasy"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historical"},{"id":"11","name":"Horror"},{"id":"12","name":"Josei"},{"id":"13","name":"Martial Arts"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystery"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychological"},{"id":"19","name":"Romance"},{"id":"20","name":"School Life"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shoujo"},{"id":"24","name":"Shoujo Ai"},{"id":"25","name":"Shounen"},{"id":"26","name":"Shounen Ai"},{"id":"27","name":"Slice of Life"},{"id":"28","name":"Sports"},{"id":"29","name":"Supernatural"},{"id":"30","name":"Tragedy"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"}],"tags":"null"}"""
        "https://www.op-vf.com" -> """{"language":"fr","name":"Op-VF","base_url":"https://www.op-vf.com","supports_latest":true,"isNsfw":false,"item_url":"https://www.op-vf.com/manga/","categories":[],"tags":"null"}"""
        "https://www.hentaishark.com"-> """{"language":"other","name":"HentaiShark","base_url":"https://www.hentaishark.com","supports_latest":true,"isNsfw":true,"item_url":"https://www.hentaishark.com/manga/","categories":[{"id":"1","name":"Doujinshi"},{"id":"2","name":"Manga"},{"id":"3","name":"Western"},{"id":"4","name":"non-h"},{"id":"5","name":"imageset"},{"id":"6","name":"artistcg"},{"id":"7","name":"misc"}],"tags":"null"}"""
        else -> MMRCMSJsonGen(name, baseUrl, lang).generateJson()
    },
) : HttpSource() {
    /**
     * Parse a List of JSON sources into a list of `MyMangaReaderCMSSource`s
     *
     * Example JSON :
     * ```
     *     {
     *         "language": "en",
     *         "name": "Example manga reader",
     *         "base_url": "https://example.com",
     *         "supports_latest": true,
     *         "item_url": "https://example.com/manga/",
     *         "categories": [
     *             {"id": "stuff", "name": "Stuff"},
     *             {"id": "test", "name": "Test"}
     *         ],
     *         "tags": [
     *             {"id": "action", "name": "Action"},
     *             {"id": "adventure", "name": "Adventure"}
     *         ]
     *     }
     *
     *
     * Sources that do not supports tags may use `null` instead of a list of json objects
     *
     * @param sourceString The List of JSON strings 1 entry = one source
     * @return The list of parsed sources
     *
     * isNSFW, language, name and base_url are no longer needed as that is handled by multisrc
     * supports_latest, item_url, categories and tags are still needed
     *
     *
     */
    val parser = JsonParser()
    val jsonObject = parser.parse(sourceInfo) as JsonObject
    override val supportsLatest = jsonObject["supports_latest"].bool
    val itemUrl = jsonObject["item_url"].string
    val categoryMappings = mapToPairs(jsonObject["categories"].array)
    var tagMappings =  if (jsonObject["tags"].isJsonArray) {
        mapToPairs(jsonObject["tags"].asJsonArray)
    } else {
        emptyList<Pair<String, String>>()
    }

    /**
     * Map an array of JSON objects to pairs. Each JSON object must have
     * the following properties:
     *
     * id: first item in pair
     * name: second item in pair
     *
     * @param array The array to process
     * @return The new list of pairs
     */
    fun mapToPairs(array: JsonArray): List<Pair<String, String>> = array.map {
        it as JsonObject

        it["id"].string to it["name"].string
    }





    private val jsonParser = JsonParser()
    private val itemUrlPath = Uri.parse(itemUrl).pathSegments.firstOrNull()
    private val parsedBaseUrl = Uri.parse(baseUrl)


    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/filterList?page=$page&sortBy=views&asc=false", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url: Uri.Builder
        when {
            name == "Mangas.pw" -> {
                url = Uri.parse("$baseUrl/search")!!.buildUpon()
                url.appendQueryParameter("q", query)
            }
            query.isNotBlank() -> {
                url = Uri.parse("$baseUrl/search")!!.buildUpon()
                url.appendQueryParameter("query", query)
            }
            else -> {
                url = Uri.parse("$baseUrl/filterList?page=$page")!!.buildUpon()
                filters.filterIsInstance<UriFilter>()
                    .forEach { it.addToUri(url) }
            }
        }
        return GET(url.toString(), headers)
    }

    /**
     * If the usual search engine isn't available, search through the list of titles with this
     */
    private fun selfSearch(query: String): Observable<MangasPage> {
        return client.newCall(GET("$baseUrl/changeMangaList?type=text", headers))
            .asObservableSuccess()
            .map { response ->
                val mangas = response.asJsoup().select("ul.manga-list a")
                    .filter { it.text().contains(query, ignoreCase = true) }
                    .map {
                        SManga.create().apply {
                            title = it.text()
                            setUrlWithoutDomain(it.attr("abs:href"))
                            thumbnail_url = coverGuess(null, it.attr("abs:href"))
                        }
                    }
                MangasPage(mangas, false)
            }
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest-release?page=$page", headers)

    override fun popularMangaParse(response: Response) = internalMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage {
        return if (listOf("query", "q").any { it in response.request().url().queryParameterNames() }) {
            // If a search query was specified, use search instead!
            val jsonArray = jsonParser.parse(response.body()!!.string()).let {
                if (name == "Mangas.pw") it.array else it["suggestions"].array
            }
            MangasPage(
                jsonArray
                    .map {
                        SManga.create().apply {
                            val segment = it["data"].string
                            url = getUrlWithoutBaseUrl(itemUrl + segment)
                            title = it["value"].string

                            // Guess thumbnails
                            // thumbnail_url = "$baseUrl/uploads/manga/$segment/cover/cover_250x350.jpg"
                        }
                    },
                false
            )
        } else {
            internalMangaParse(response)
        }
    }

    private val latestTitles = mutableSetOf<String>()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.location().contains("page=1")) latestTitles.clear()

        val mangas = document.select(latestUpdatesSelector())
            .let { elements ->
                when {
                    // Mangas.pw
                    elements.select("a.fa-info-circle + a").firstOrNull()?.hasText() == true -> elements.map { latestUpdatesFromElement(it, "a.fa-info-circle + a") }
                    // List layout (most sources)
                    elements.select("a").firstOrNull()?.hasText() == true -> elements.map { latestUpdatesFromElement(it, "a") }
                    // Grid layout (e.g. MangaID)
                    else -> document.select(gridLatestUpdatesSelector()).map { gridLatestUpdatesFromElement(it) }
                }
            }
            .filterNotNull()

        return MangasPage(mangas, document.select(latestUpdatesNextPageSelector()) != null)
    }
    private fun latestUpdatesSelector() = "div.mangalist div.manga-item"
    private fun latestUpdatesNextPageSelector() = "a[rel=next]"
    private fun latestUpdatesFromElement(element: Element, urlSelector: String): SManga? {
        return element.select(urlSelector).first().let { titleElement ->
            if (titleElement.text() in latestTitles) {
                null
            } else {
                latestTitles.add(titleElement.text())
                SManga.create().apply {
                    url = titleElement.attr("abs:href").substringAfter(baseUrl) // intentionally not using setUrlWithoutDomain
                    title = titleElement.text().trim()
                    thumbnail_url = "$baseUrl/uploads/manga/${url.substringAfterLast('/')}/cover/cover_250x350.jpg"
                }
            }
        }
    }
    private fun gridLatestUpdatesSelector() = "div.mangalist div.manga-item, div.grid-manga tr"
    private fun gridLatestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        element.select("a.chart-title").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }

    private fun internalMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val internalMangaSelector = when (name) {
            "Utsukushii" -> "div.content div.col-sm-6"
            else -> "div[class^=col-sm], div.col-xs-6"
        }
        return MangasPage(
            document.select(internalMangaSelector).map {
                SManga.create().apply {
                    val urlElement = it.getElementsByClass("chart-title")
                    if (urlElement.size == 0) {
                        url = getUrlWithoutBaseUrl(it.select("a").attr("href"))
                        title = it.select("div.caption").text()
                        it.select("div.caption div").text().let { if (it.isNotEmpty()) title = title.substringBefore(it) } // To clean submanga's titles without breaking hentaishark's
                    } else {
                        url = getUrlWithoutBaseUrl(urlElement.attr("href"))
                        title = urlElement.text().trim()
                    }

                    it.select("img").let { img ->
                        thumbnail_url = when {
                            it.hasAttr("data-background-image") -> it.attr("data-background-image") // Utsukushii
                            img.hasAttr("data-src") -> coverGuess(img.attr("abs:data-src"), url)
                            else -> coverGuess(img.attr("abs:src"), url)
                        }
                    }
                }
            },
            document.select(".pagination a[rel=next]").isNotEmpty()
        )
    }

    // Guess thumbnails on broken websites
    public fun coverGuess(url: String?, mangaUrl: String): String? {
        return if (url?.endsWith("no-image.png") == true) {
            "$baseUrl/uploads/manga/${mangaUrl.substringAfterLast('/')}/cover/cover_250x350.jpg"
        } else {
            url
        }
    }

    public fun getUrlWithoutBaseUrl(newUrl: String): String {
        val parsedNewUrl = Uri.parse(newUrl)
        val newPathSegments = parsedNewUrl.pathSegments.toMutableList()

        for (i in parsedBaseUrl.pathSegments) {
            if (i.trim().equals(newPathSegments.first(), true)) {
                newPathSegments.removeAt(0)
            } else break
        }

        val builtUrl = parsedNewUrl.buildUpon().path("/")
        newPathSegments.forEach { builtUrl.appendPath(it) }

        var out = builtUrl.build().encodedPath!!
        if (parsedNewUrl.encodedQuery != null)
            out += "?" + parsedNewUrl.encodedQuery
        if (parsedNewUrl.encodedFragment != null)
            out += "#" + parsedNewUrl.encodedFragment

        return out
    }

    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        document.select("h2.listmanga-header, h2.widget-title").firstOrNull()?.text()?.trim()?.let { title = it }
        thumbnail_url = coverGuess(document.select(".row [class^=img-responsive]").firstOrNull()?.attr("abs:src"), document.location())
        description = document.select(".row .well p").text().trim()

        val detailAuthor = setOf("author(s)", "autor(es)", "auteur(s)", "著作", "yazar(lar)", "mangaka(lar)", "pengarang/penulis", "pengarang", "penulis", "autor", "المؤلف", "перевод", "autor/autorzy")
        val detailArtist = setOf("artist(s)", "artiste(s)", "sanatçi(lar)", "artista(s)", "artist(s)/ilustrator", "الرسام", "seniman", "rysownik/rysownicy")
        val detailGenre = setOf("categories", "categorías", "catégories", "ジャンル", "kategoriler", "categorias", "kategorie", "التصنيفات", "жанр", "kategori", "tagi")
        val detailStatus = setOf("status", "statut", "estado", "状態", "durum", "الحالة", "статус")
        val detailStatusComplete = setOf("complete", "مكتملة", "complet", "completo", "zakończone")
        val detailStatusOngoing = setOf("ongoing", "مستمرة", "en cours", "em lançamento", "prace w toku")
        val detailDescription = setOf("description", "resumen")

        for (element in document.select(".row .dl-horizontal dt")) {
            when (element.text().trim().toLowerCase()) {
                in detailAuthor -> author = element.nextElementSibling().text()
                in detailArtist -> artist = element.nextElementSibling().text()
                in detailGenre -> genre = element.nextElementSibling().select("a").joinToString {
                    it.text().trim()
                }
                in detailStatus -> status = when (element.nextElementSibling().text().trim().toLowerCase()) {
                    in detailStatusComplete -> SManga.COMPLETED
                    in detailStatusOngoing -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
        // When details are in a .panel instead of .row (ES sources)
        for (element in document.select("div.panel span.list-group-item")) {
            when (element.select("b").text().toLowerCase().substringBefore(":")) {
                in detailAuthor -> author = element.select("b + a").text()
                in detailArtist -> artist = element.select("b + a").text()
                in detailGenre -> genre = element.getElementsByTag("a").joinToString {
                    it.text().trim()
                }
                in detailStatus -> status = when (element.select("b + span.label").text().toLowerCase()) {
                    in detailStatusComplete -> SManga.COMPLETED
                    in detailStatusOngoing -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
                in detailDescription -> description = element.ownText()
            }
        }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * Overriden to allow for null chapters
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapNotNull { nullableChapterFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    private fun chapterListSelector() = "ul[class^=chapters] > li:not(.btn), table.table tr"
    // Some websites add characters after "chapters" thus the need of checking classes that starts with "chapters"

    /**
     * titleWrapper can have multiple "a" elements, filter to the first that contains letters (i.e. not "" or # as is possible)
     */
    private val urlRegex = Regex("""[a-zA-z]""")

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    private fun nullableChapterFromElement(element: Element): SChapter? {
        val chapter = SChapter.create()

        try {
            val titleWrapper = if (name == "Mangas.pw") element.select("i a").first() else element.select("[class^=chapter-title-rtl]").first()
            // Some websites add characters after "..-rtl" thus the need of checking classes that starts with that
            val url = titleWrapper.getElementsByTag("a")
                .first { it.attr("href").contains(urlRegex) }
                .attr("href")

            // Ensure chapter actually links to a manga
            // Some websites use the chapters box to link to post announcements
            // The check is skipped if mangas are stored in the root of the website (ex '/one-piece' without a segment like '/manga/one-piece')
            if (itemUrlPath != null && !Uri.parse(url).pathSegments.firstOrNull().equals(itemUrlPath, true)) {
                return null
            }

            chapter.url = getUrlWithoutBaseUrl(url)
            chapter.name = titleWrapper.text()

            // Parse date
            val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()
            chapter.date_upload = parseDate(dateText)

            return chapter
        } catch (e: NullPointerException) {
            // For chapter list in a table
            if (element.select("td").hasText()) {
                element.select("td a").let {
                    chapter.setUrlWithoutDomain(it.attr("href"))
                    chapter.name = it.text()
                }
                val tableDateText = element.select("td + td").text()
                chapter.date_upload = parseDate(tableDateText)

                return chapter
            }
        }

        return null
    }

    private fun parseDate(dateText: String): Long {
        return try {
            DATE_FORMAT.parse(dateText)?.time ?: 0
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(response: Response) = response.asJsoup().select("#all > .img-responsive")
        .mapIndexed { i, e ->
            var url = (if (e.hasAttr("data-src")) e.attr("abs:data-src") else e.attr("abs:src")).trim()

            // Mangas.pw encodes some of their urls, decode them
            if (name.contains("Mangas.pw") && !url.contains(".")) {
                url = Base64.decode(url.substringAfter("//"), Base64.DEFAULT).toString(Charsets.UTF_8).substringBefore("=")
                url = URLDecoder.decode(url, "UTF-8")
            }

            Page(i, "", url)
        }


    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

    private fun getInitialFilterList() = listOf<Filter<*>>(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        UriSelectFilter(
            "Category",
            "cat",
            arrayOf(
                "" to "Any",
                *categoryMappings.toTypedArray()
            )
        ),
        UriSelectFilter(
            "Begins with",
            "alpha",
            arrayOf(
                "" to "Any",
                *"#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray().map {
                    Pair(it.toString(), it.toString())
                }.toTypedArray()
            )
        ),
        SortFilter()
    )

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList(): FilterList {
        return when {
            name == "Mangas.pw" -> FilterList()
            tagMappings != null -> {
                FilterList(
                    getInitialFilterList() + UriSelectFilter(
                        "Tag",
                        "tag",
                        arrayOf(
                            "" to "Any",
                            *tagMappings.toTypedArray()
                        )
                    )
                )
            }
            else -> FilterList(getInitialFilterList())
        }
    }

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    open class UriSelectFilter(
        displayName: String,
        private val uriParam: String,
        private val vals: Array<Pair<String, String>>,
        private val firstIsUnspecified: Boolean = true,
        defaultValue: Int = 0
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    class AuthorFilter : Filter.Text("Author"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("author", state)
        }
    }

    class SortFilter :
        Filter.Sort(
            "Sort",
            sortables.map { it.second }.toTypedArray(),
            Selection(0, true)
        ),
        UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("sortBy", sortables[state!!.index].first)
            uri.appendQueryParameter("asc", state!!.ascending.toString())
        }

        companion object {
            private val sortables = arrayOf(
                "name" to "Name",
                "views" to "Popularity",
                "last_release" to "Last update"
            )
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("d MMM. yyyy", Locale.US)
    }
}
