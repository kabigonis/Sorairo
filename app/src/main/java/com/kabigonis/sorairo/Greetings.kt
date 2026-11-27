package com.kabigonis.sorairo

// Local greetings for the world clock cards, written in Latin letters so anyone can read them.
// Each language has four: morning, afternoon, evening, night.

internal enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

internal fun dayPartFor(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    in 17..21 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

private fun g(a: String, b: String = a, c: String = b, d: String = c) = arrayOf(a, b, c, d)

private val GREETINGS: Map<String, Array<String>> = mapOf(
    "en" to g("good morning", "good afternoon", "good evening", "good night"),
    "ja" to g("ohayou", "konnichiwa", "konbanwa", "oyasumi"),
    "zh" to g("zaoshang hao", "ni hao", "wanshang hao", "wan an"),
    "ko" to g("joeun achim", "annyeonghaseyo", "joeun jeonyeok", "annyeonghi jumuseyo"),
    "hi" to g("suprabhat", "namaste", "shubh sandhya", "shubh ratri"),
    "ur" to g("subah bakhair", "assalam o alaikum", "shaam bakhair", "shab bakhair"),
    "bn" to g("shubho shokal", "nomoshkar", "shubho sondhya", "shubho ratri"),
    "ne" to g("suprabhat", "namaste", "shubha sandhya", "shubha ratri"),
    "fr" to g("bonjour", "bonjour", "bonsoir", "bonne nuit"),
    "es" to g("buenos dias", "buenas tardes", "buenas noches", "buenas noches"),
    "pt" to g("bom dia", "boa tarde", "boa noite", "boa noite"),
    "de" to g("guten morgen", "guten tag", "guten abend", "gute nacht"),
    "it" to g("buongiorno", "buon pomeriggio", "buonasera", "buonanotte"),
    "ru" to g("dobroye utro", "dobryy den", "dobryy vecher", "spokoynoy nochi"),
    "uk" to g("dobroho ranku", "dobryi den", "dobryi vechir", "na dobranich"),
    "pl" to g("dzien dobry", "dzien dobry", "dobry wieczor", "dobranoc"),
    "cs" to g("dobre rano", "dobry den", "dobry vecer", "dobrou noc"),
    "sk" to g("dobre rano", "dobry den", "dobry vecer", "dobru noc"),
    "sl" to g("dobro jutro", "dober dan", "dober vecer", "lahko noc"),
    "hr" to g("dobro jutro", "dobar dan", "dobra vecer", "laku noc"),
    "bg" to g("dobro utro", "dobar den", "dobar vecher", "leka nosht"),
    "ro" to g("buna dimineata", "buna ziua", "buna seara", "noapte buna"),
    "hu" to g("jo reggelt", "jo napot", "jo estet", "jo ejszakat"),
    "el" to g("kalimera", "yassou", "kalispera", "kalinichta"),
    "tr" to g("gunaydin", "iyi gunler", "iyi aksamlar", "iyi geceler"),
    "nl" to g("goedemorgen", "goedemiddag", "goedenavond", "welterusten"),
    "sv" to g("god morgon", "hej", "god kvall", "god natt"),
    "no" to g("god morgen", "hei", "god kveld", "god natt"),
    "da" to g("godmorgen", "goddag", "godaften", "godnat"),
    "fi" to g("hyvaa huomenta", "hei", "hyvaa iltaa", "hyvaa yota"),
    "is" to g("godan daginn", "godan daginn", "gott kvold", "god nott"),
    "lt" to g("labas rytas", "laba diena", "labas vakaras", "labanakt"),
    "lv" to g("labrit", "labdien", "labvakar", "ar labunakti"),
    "et" to g("tere hommikust", "tere paevast", "tere ohtust", "head ood"),
    "ar" to g("sabah al-khayr", "marhaba", "masa al-khayr", "tusbih ala khayr"),
    "he" to g("boker tov", "shalom", "erev tov", "laila tov"),
    "fa" to g("sobh bekheir", "salam", "asr bekheir", "shab bekheir"),
    "hy" to g("bari luys", "barev", "bari yerek", "bari gisher"),
    "ka" to g("gamarjoba"),
    "az" to g("sabahiniz xeyir", "salam", "axsamiz xeyir", "geceniz xeyir"),
    "uz" to g("xayrli tong", "salom", "xayrli kech", "xayrli tun"),
    "mn" to g("sain baina uu"),
    "id" to g("selamat pagi", "selamat siang", "selamat sore", "selamat malam"),
    "ms" to g("selamat pagi", "selamat tengah hari", "selamat petang", "selamat malam"),
    "th" to g("sawasdee", "sawasdee", "sawasdee", "fan di"),
    "vi" to g("chao buoi sang", "xin chao", "chao buoi toi", "chuc ngu ngon"),
    "tl" to g("magandang umaga", "magandang hapon", "magandang gabi", "magandang gabi"),
    "km" to g("arun suosdei", "suosdei", "sayoun suosdei", "reatrey suosdei"),
    "lo" to g("sabaidee"),
    "my" to g("mingalaba"),
    "si" to g("ayubowan"),
    "sw" to g("habari za asubuhi", "hujambo", "habari za jioni", "usiku mwema"),
    "am" to g("selam"),
    "mi" to g("kia ora"),
    "fj" to g("bula"),
    "sm" to g("talofa"),
    "to" to g("malo e lelei"),
    "dv" to g("assalaamu alaikum"),
    "sq" to g("mire mengjes", "mirdita", "mirembrema", "naten e mire")
)

private val LANGUAGE_COUNTRIES: Map<String, String> = mapOf(
    "ja" to "Japan",
    "zh" to "China|Taiwan|Hong Kong|Macau",
    "ko" to "South Korea|North Korea",
    "hi" to "India",
    "ur" to "Pakistan",
    "bn" to "Bangladesh",
    "ne" to "Nepal|Bhutan",
    "fr" to "France|Monaco|Haiti|French Guiana|French Polynesia|New Caledonia|Guadeloupe|Martinique|Mayotte|Réunion|" +
        "Wallis & Futuna|St Barthelemy|St Martin (French)|St Pierre & Miquelon|Congo (Dem. Rep.)|DR Congo|Congo (Rep.)|" +
        "Cote d'Ivoire|Senegal|Mali|Burkina Faso|Niger|Guinea|Togo|Benin|Gabon|Cameroon|Chad|Central African Rep.|" +
        "Madagascar|Comoros|Luxembourg|Belgium|Rwanda|Burundi|Seychelles|Mauritius",
    "es" to "Spain|Mexico|Argentina|Chile|Colombia|Peru|Venezuela|Ecuador|Bolivia|Uruguay|Paraguay|Cuba|" +
        "Dominican Republic|Guatemala|Honduras|El Salvador|Nicaragua|Costa Rica|Panama|Puerto Rico|Equatorial Guinea|Andorra",
    "pt" to "Portugal|Brazil|Angola|Mozambique|Cape Verde|Guinea-Bissau|Sao Tome & Principe|Timor-Leste",
    "de" to "Germany|Austria|Liechtenstein|Switzerland",
    "it" to "Italy|San Marino|Vatican City",
    "ru" to "Russia|Belarus|Kazakhstan|Kyrgyzstan",
    "uk" to "Ukraine",
    "pl" to "Poland",
    "cs" to "Czechia",
    "sk" to "Slovakia",
    "sl" to "Slovenia",
    "hr" to "Croatia|Bosnia and Herzegovina|Montenegro|Serbia|North Macedonia",
    "bg" to "Bulgaria",
    "ro" to "Romania|Moldova",
    "hu" to "Hungary",
    "el" to "Greece|Cyprus",
    "tr" to "Turkey",
    "nl" to "Netherlands|Suriname|Aruba|Curaçao|Caribbean NL|St Maarten (Dutch)",
    "sv" to "Sweden|Åland Islands",
    "no" to "Norway|Svalbard & Jan Mayen",
    "da" to "Denmark|Faroe Islands|Greenland",
    "fi" to "Finland",
    "is" to "Iceland",
    "lt" to "Lithuania",
    "lv" to "Latvia",
    "et" to "Estonia",
    "ar" to "Saudi Arabia|United Arab Emirates|Qatar|Kuwait|Bahrain|Oman|Yemen|Iraq|Syria|Lebanon|Jordan|Palestine|" +
        "Egypt|Libya|Algeria|Tunisia|Morocco|Western Sahara|Sudan|Mauritania|Djibouti|Somalia",
    "he" to "Israel",
    "fa" to "Iran|Afghanistan",
    "hy" to "Armenia",
    "ka" to "Georgia",
    "az" to "Azerbaijan",
    "uz" to "Uzbekistan|Tajikistan|Turkmenistan",
    "mn" to "Mongolia",
    "id" to "Indonesia",
    "ms" to "Malaysia|Brunei",
    "th" to "Thailand",
    "vi" to "Vietnam",
    "tl" to "Philippines",
    "km" to "Cambodia",
    "lo" to "Laos",
    "my" to "Myanmar",
    "si" to "Sri Lanka",
    "sw" to "Kenya|Tanzania|Uganda",
    "am" to "Ethiopia",
    "mi" to "New Zealand",
    "fj" to "Fiji",
    "sm" to "Samoa|Samoa (American)",
    "to" to "Tonga",
    "dv" to "Maldives",
    "sq" to "Albania"
)

private val COUNTRY_LANGUAGE: Map<String, String> = HashMap<String, String>().apply {
    for ((lang, countries) in LANGUAGE_COUNTRIES) {
        for (c in countries.split('|')) put(c, lang)
    }
}

internal fun languageForCountry(country: String?): String = COUNTRY_LANGUAGE[country ?: ""] ?: "en"

/** "konbanwa" for Japan in the evening; English wording for countries without an entry. */
internal fun greetingFor(country: String?, hour: Int): String {
    val set = GREETINGS[languageForCountry(country)] ?: GREETINGS.getValue("en")
    return set[dayPartFor(hour).ordinal]
}

internal fun capitalizeFirst(s: String): String = if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)
