package pl.walbrzych.autobus.data

import android.content.Context
import androidx.core.content.edit

/** One MyBus operator configuration, copied from the application's local city catalogue. */
data class CityConfig(
    val id: Int,
    val name: String,
    val operator: String,
    val baseUrl: String,
    val cityCode: String,
)

/**
 * The MyBus APK builds this list locally rather than downloading it. Keeping the same
 * immutable catalogue makes a chosen city an explicit, reproducible configuration.
 */
object CityCatalog {
    val cities: List<CityConfig> = listOf(
        CityConfig(14, "Biała Podlaska", "MZK", "http://rozklad.mzkbp.pl/AndroidService/SchedulesService.svc", "BIALA"),
        CityConfig(11, "Bolesławiec", "MZK", "http://83.13.186.174:8081/AndroidService/SchedulesService.svc", "BOLES"),
        CityConfig(54, "Chełm", "CLA", "http://rozklad.cla.net.pl/myBusServices/SchedulesService.svc", "CHELM"),
        CityConfig(46, "Cieszyn", "ZGK", "http://rozklad.zgk.cieszyn.pl/myBusServices/SchedulesService.svc", "CIESZ"),
        CityConfig(7, "Dębica", "MKS", "http://rj.mks.debica.pl/AndroidService/SchedulesService.svc", "DEBIC"),
        CityConfig(5, "Elbląg", "ZKM", "http://www.rozklad.zkm.elblag.com.pl/AndroidService/SchedulesService.svc", "ELBLA"),
        CityConfig(4, "Gdańsk", "GAiT", "http://info.gait.pl/SchedulesService/SchedulesService.svc", "GDANS"),
        CityConfig(30, "Głogów", "KM", "http://89.151.3.5/myBusServices/SchedulesService.svc", "GLOGO"),
        CityConfig(48, "Inowrocław", "MPK", "http://bus.inowroclaw.pl/myBusServices/SchedulesService.svc", "INOWR"),
        CityConfig(33, "Jastrzębie-Zdrój", "MZK", "https://bilet.mzkjastrzebie.com:8088/myBusServices/SchedulesService.svc", "JASTR"),
        CityConfig(28, "Jelenia Góra", "MZK", "http://www.seba.mzk.jgora.pl:8080/myBusServices/SchedulesService.svc", "JELGO"),
        CityConfig(49, "Kędzierzyn-Koźle", "MZK", "http://79.188.1.197/SchedulesService.svc", "KKOZL"),
        CityConfig(12, "Kielce", "ZTM", "http://sip.ztm.kielce.pl/AndroidService/SchedulesService.svc", "KIELC"),
        CityConfig(59, "Koleje Małopolskie", "KMŁ", "https://mybus.kolejemalopolskie.com.pl/SchedulesService.svc", "KOMAL"),
        CityConfig(18, "Kołobrzeg", "KM", "http://89.239.105.31/myBusServices/SchedulesService.svc", "KOLOB"),
        CityConfig(13, "Kraśnik", "MPK", "http://176.104.119.244/myBusServices/SchedulesService.svc", "KRASN"),
        CityConfig(43, "Krosno", "MKS", "http://rozklad.mks-krosno.pl/myBusServices/SchedulesService.svc", "KROSN"),
        CityConfig(52, "Kutno", "MZK", "http://rozklad.km.kutno.pl/myBusServices/SchedulesService.svc", "KUTNO"),
        CityConfig(39, "Legnica", "MPK", "http://mybus.autobusy.legnica.eu/SchedulesService.svc", "LEGNI"),
        CityConfig(35, "Leszno", "MZK", "http://rozklad.mzk.leszno.pl/AndroidService/SchedulesService.svc", "LESZN"),
        CityConfig(2, "Lublin", "ZTM", "http://sip.ztm.lublin.eu/AndroidService/SchedulesService.svc", "LUBLI"),
        CityConfig(25, "Łódź", "ZDiT", "http://rozklady.lodz.pl/myBusServices/SchedulesService.svc", "LODZ "),
        CityConfig(44, "Łowicz", "MZK", "http://rozklad.mzklowicz.pl/myBusServices/SchedulesService.svc", "LOWIC"),
        CityConfig(1, "Mielec", "MKS", "http://mapa.mks-mielec.pl/AndroidService/SchedulesService.svc", "MIELE"),
        CityConfig(31, "Olsztyn", "ZDZiT", "http://sip.zdzit.olsztyn.eu/myBusServices/SchedulesService.svc", "OLSZT"),
        CityConfig(20, "Ostrołęka", "MZK", "http://rozklad.mzk.ostroleka.pl/myBusServices/SchedulesService.svc", "OLEKA"),
        CityConfig(15, "Ostrów Wielkopolski", "MZK", "http://185.22.8.43/myBusServices/SchedulesService.svc", "OSTRO"),
        CityConfig(57, "Ostrowiec Świętokrzyski", "MZK", "http://rozklad.mzkostrowiec.pl/myBusServices/SchedulesService.svc", "OSTSW"),
        CityConfig(56, "Pabianice", "MZK", "http://mybus.komunikacjapabianice.pl/SchedulesService.svc", "PABIA"),
        CityConfig(17, "Płock", "KM", "https://rozkladjazdy.kmplock.eu/myBusServices/SchedulesService.svc", "PLOCK"),
        CityConfig(42, "Polkowice", "ZKM", "http://zkmp.com.pl/myBusServices/SchedulesService.svc", "POLKO"),
        CityConfig(45, "Przemyśl", "MZK", "http://rozklad.mzk.przemysl.pl/myBusServices/SchedulesService.svc", "PRMZK"),
        CityConfig(8, "Puławy", "MZK", "http://mapa.mzk.pulawy.pl/AndroidService/SchedulesService.svc", "PULAW"),
        CityConfig(23, "Radom", "MZDiK", "http://31.11.251.95/myBusServices/SchedulesService.svc", "RADOM"),
        CityConfig(36, "Radomsko", "MPK", "http://mpk.mpk-radomsko.pl:8081/SchedulesService.svc", "RDMSK"),
        CityConfig(27, "Rybnik", "KM", "http://rozklad.km.rybnik.pl/myBusServices/SchedulesService.svc", "RYBNI"),
        CityConfig(29, "Rzeszów", "ZTM", "https://mybus.erzeszow.pl/myBusServices/SchedulesService.svc", "RZZTM"),
        CityConfig(26, "Sanok", "SPGK", "http://rozkladjazdy.spgk.com.pl/myBusServices/SchedulesService.svc", "SANOK"),
        CityConfig(40, "Šiauliai", "Busturas", "http://kis.busturas.lt/myBusServices/SchedulesService.svc", "SIAUL"),
        CityConfig(21, "Siedlce", "MPK", "https://rozklad.mpk.siedlce.pl/myBusServices/SchedulesService.svc", "SIEDL"),
        CityConfig(37, "Słupsk", "ZIM", "http://176.97.80.62/myBusServices/SchedulesService.svc", "ZIMSL"),
        CityConfig(16, "Stalowa Wola", "MZK", "https://mybus.mzk.stalowa-wola.pl/SchedulesService.svc", "STALO"),
        CityConfig(55, "Starachowice", "ZEC", "http://rozklad-jazdy.zecstar.pl/myBusServices/SchedulesService.svc", "STRCH"),
        CityConfig(9, "Suwałki", "PGK", "http://89.22.39.6/AndroidService/SchedulesService.svc", "SUWAL"),
        CityConfig(6, "Świdnica", "MPK", "http://www.grj.mpk.swidnica.pl/AndroidService/SchedulesService.svc", "SWIDN"),
        CityConfig(41, "Świebodzice", "ZGK", "http://213.108.80.2:8085/SchedulesService.svc", "SWIEB"),
        CityConfig(47, "Tábor", "COMETT PLUS", "http://www.mhd-tabor.comettplus.cz/myBusServices/SchedulesService.svc", "TABOR"),
        CityConfig(32, "Tarnowskie Góry", "MZKP", "http://157.25.157.76/myBusServices/SchedulesService.svc", "NOWAK"),
        CityConfig(22, "Tarnów", "ZDiK", "http://80.85.231.251/myBusServices/SchedulesService.svc", "TARNO"),
        CityConfig(50, "Tczew", "Gryf", "http://www.rozklady.tczew.pl/myBusServices/SchedulesService.svc", "TCZEW"),
        CityConfig(10, "Wałbrzych", "Gmina Wałbrzych", "http://rozklad.walbrzych.eu/myBusServices/SchedulesService.svc", "WALBR"),
        CityConfig(58, "Zamość", "MZK", "http://37.109.29.250/myBusServices/SchedulesService.svc", "ZAMOS"),
        CityConfig(53, "Žilina", "DPMZ", "https://www.mybus.dpmz.sk/myBusServices/SchedulesService.svc", "ZILIN"),
    )

    fun byId(id: Int?): CityConfig? = cities.firstOrNull { it.id == id }
}

/** Persistent, single source of truth for the operator chosen by the user. */
class CitySelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selectedCity(): CityConfig? = CityCatalog.byId(preferences.getInt(ACTIVE_CITY_ID, NO_CITY))

    fun select(city: CityConfig) {
        preferences.edit(commit = true) { putInt(ACTIVE_CITY_ID, city.id) }
    }

    private companion object {
        const val PREFERENCES = "city_selection"
        const val ACTIVE_CITY_ID = "active_city_id"
        const val NO_CITY = -1
    }
}
