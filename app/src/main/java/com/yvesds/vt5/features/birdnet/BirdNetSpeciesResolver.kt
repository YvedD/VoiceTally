package com.yvesds.vt5.features.birdnet

import android.content.Context
import android.util.Log
import com.yvesds.vt5.features.serverdata.model.ServerDataCache

/**
 * BirdNetSpeciesResolver - Mapt wetenschappelijke (Latijnse) en Engelse vogelnamen
 * van BirdNET-GO naar de officiële Nederlandse soortnamen uit de VT5 database (species.json).
 */
object BirdNetSpeciesResolver {

    private const val TAG = "BirdNetSpeciesResolver"

    // Snelle in-memory cache van Latijnse naam -> Nederlandse naam
    private val latinToDutchMap = HashMap<String, String>()
    // Snelle in-memory cache van Soortnaam -> Nederlandse naam
    private val nameToDutchMap = HashMap<String, String>()

    @Volatile
    private var isInitialized = false

    /**
     * Initialiseert de naam-mappingtabel op basis van `ServerDataCache` (species.json).
     */
    fun ensureInitialized(context: Context) {
        if (isInitialized && latinToDutchMap.isNotEmpty()) return

        synchronized(this) {
            if (isInitialized && latinToDutchMap.isNotEmpty()) return

            try {
                val snapshot = ServerDataCache.getCachedOrNull() ?: return
                val speciesList = snapshot.speciesById.values

                for (sp in speciesList) {
                    val dutchName = sp.soortnaam.trim()
                    if (dutchName.isBlank()) continue

                    // Latijnse naam opslaan
                    val latin = sp.latin.trim().lowercase()
                    if (latin.isNotBlank()) {
                        latinToDutchMap[latin] = dutchName
                        
                        // Ook zonder ondersoort (3e woord) opslaan als fallback
                        val latinParts = latin.split(" ")
                        if (latinParts.size > 2) {
                            val genusSpecies = "${latinParts[0]} ${latinParts[1]}"
                            latinToDutchMap.putIfAbsent(genusSpecies, dutchName)
                        }
                    }

                    // Nederlandse naam zelf opzoeken
                    nameToDutchMap[dutchName.lowercase()] = dutchName
                }

                if (latinToDutchMap.isNotEmpty()) {
                    isInitialized = true
                    Log.i(TAG, "BirdNetSpeciesResolver geïnitialiseerd met ${latinToDutchMap.size} Latijn-NL mappings")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kon ServerDataCache niet laden voor species mapping: ${e.message}")
            }
        }
    }

    /**
     * Vertaalt een Latijnse wetenschappelijke naam of Engelse naam naar de Nederlandse soortnaam.
     *
     * Voorbeeld:
     * - "Merops apiaster" -> "Bijeneter"
     * - "Fringilla coelebs" -> "Vink"
     * - "Apus apus" -> "Gierzwaluw"
     * - "European Bee-eater" -> "Bijeneter"
     */
    fun resolveDutchName(context: Context, scientificName: String, commonName: String = ""): String {
        return resolveSpecies(context, scientificName, commonName).dutchName
    }

    data class ResolvedSpeciesInfo(
        val soortId: String,
        val dutchName: String
    )

    /**
     * Resolvet zowel het unieke VT5 `soortid` als de Nederlandse `dutchName` van een vogel.
     */
    fun resolveSpecies(context: Context, scientificName: String, commonName: String = ""): ResolvedSpeciesInfo {
        ensureInitialized(context)

        val cleanScientific = scientificName.trim().lowercase()
        val cleanCommon = commonName.trim().lowercase()

        // 1. Zoek in ServerDataCache op Latijnse/wetenschappelijke naam
        try {
            val snapshot = ServerDataCache.getCachedOrNull()
            if (snapshot != null) {
                if (cleanScientific.isNotBlank()) {
                    val matchByLatin = snapshot.speciesById.values.find {
                        it.latin.trim().lowercase() == cleanScientific
                    }
                    if (matchByLatin != null) {
                        return ResolvedSpeciesInfo(matchByLatin.soortid, matchByLatin.soortnaam)
                    }

                    // Probeer zonder ondersoort (3e woord)
                    val parts = cleanScientific.split(" ")
                    if (parts.size >= 2) {
                        val binomial = "${parts[0]} ${parts[1]}"
                        val matchBinomial = snapshot.speciesById.values.find {
                            it.latin.trim().lowercase() == binomial
                        }
                        if (matchBinomial != null) {
                            return ResolvedSpeciesInfo(matchBinomial.soortid, matchBinomial.soortnaam)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fout bij opzoeken in ServerDataCache: ${e.message}")
        }

        // 2. Directe Latijnse match in onze geheugenmap
        if (cleanScientific.isNotBlank()) {
            latinToDutchMap[cleanScientific]?.let { dutch ->
                val id = findSoortIdByDutchName(dutch) ?: dutch
                return ResolvedSpeciesInfo(id, dutch)
            }

            val parts = cleanScientific.split(" ")
            if (parts.size >= 2) {
                val binomial = "${parts[0]} ${parts[1]}"
                latinToDutchMap[binomial]?.let { dutch ->
                    val id = findSoortIdByDutchName(dutch) ?: dutch
                    return ResolvedSpeciesInfo(id, dutch)
                }
            }
        }

        // 3. Als commonName in onze geheugenmap voorkomt
        if (cleanCommon.isNotBlank()) {
            nameToDutchMap[cleanCommon]?.let { dutch ->
                val id = findSoortIdByDutchName(dutch) ?: dutch
                return ResolvedSpeciesInfo(id, dutch)
            }
            latinToDutchMap[cleanCommon]?.let { dutch ->
                val id = findSoortIdByDutchName(dutch) ?: dutch
                return ResolvedSpeciesInfo(id, dutch)
            }
        }

        // 4. Ingebouwde Engelse/Veelvoorkomende vogelnamen vertaaltabel
        val fallbackMatch = COMMON_ENGLISH_TO_DUTCH[cleanCommon]
            ?: COMMON_ENGLISH_TO_DUTCH[cleanScientific]
        if (fallbackMatch != null) {
            val id = findSoortIdByDutchName(fallbackMatch) ?: fallbackMatch
            return ResolvedSpeciesInfo(id, fallbackMatch)
        }

        val fallbackName = commonName.ifBlank { scientificName.ifBlank { "Onbekende vogel" } }
        return ResolvedSpeciesInfo(findSoortIdByDutchName(fallbackName) ?: fallbackName, fallbackName)
    }

    private fun findSoortIdByDutchName(dutchName: String): String? {
        val snapshot = ServerDataCache.getCachedOrNull() ?: return null
        val clean = dutchName.trim().lowercase()
        return snapshot.speciesById.values.find { it.soortnaam.trim().lowercase() == clean }?.soortid
    }

    /**
     * Handmatige opzoektabel voor veelvoorkomende Engelse BirdNET-GO volksnamen naar Nederlands.
     */
    private val COMMON_ENGLISH_TO_DUTCH = mapOf(
        "european bee-eater" to "Bijeneter",
        "common chaffinch" to "Vink",
        "eurasian jay" to "Gaai",
        "eurasian magpie" to "Ekster",
        "barn swallow" to "Boerenzwaluw",
        "common house martin" to "Huiszwaluw",
        "sand martin" to "Oeverzwaluw",
        "bank swallow" to "Oeverzwaluw",
        "common swift" to "Gierzwaluw",
        "eurasian skylark" to "Veldleeuwerik",
        "european robin" to "Roodborst",
        "meadow pipit" to "Graspieper",
        "tree pipit" to "Boompieper",
        "water pipit" to "Waterpieper",
        "eurasian siskin" to "Sijs",
        "common redpoll" to "Barmsijs",
        "lesser redpoll" to "Kleine Barmsijs",
        "eurasian linnet" to "Kneu",
        "european goldfinch" to "Putter",
        "eurasian greenfinch" to "Groenling",
        "eurasian bullfinch" to "Goudvink",
        "hawfinch" to "Appelvink",
        "yellowhammer" to "Geelgors",
        "common reed bunting" to "Rietgors",
        "corn bunting" to "Grauwe Gors",
        "eurasian blackcap" to "Zwartkop",
        "garden warbler" to "Tuinfluiter",
        "greater whitethroat" to "Grasmus",
        "lesser whitethroat" to "Braamsluiper",
        "common chiffchaff" to "Tjiftjaf",
        "willow warbler" to "Fitis",
        "wood warbler" to "Fluiter",
        "goldcrest" to "Goudhaan",
        "firecrest" to "Vuurgoudhaan",
        "eurasian wren" to "Winterkoning",
        "white wagtail" to "Witte Kwikstaart",
        "yellow wagtail" to "Gele Kwikstaart",
        "grey wagtail" to "Grote Gele Kwikstaart",
        "common starling" to "Spreeuw",
        "eurasian blackbird" to "Merel",
        "song thrush" to "Zanglijster",
        "mistle thrush" to "Grote Lijster",
        "redwing" to "Koperwiek",
        "fieldfare" to "Kramsvogel",
        "common wood pigeon" to "Houtduif",
        "stock dove" to "Holenduif",
        "eurasian collared dove" to "Turkse Tortel",
        "european turtle dove" to "Zomertortel",
        "common buzzard" to "Buizerd",
        "eurasian buzzard" to "Buizerd",
        "honey buzzard" to "Wespendief",
        "eurasian sparrowhawk" to "Sperwer",
        "northern goshawk" to "Havik",
        "common kestrel" to "Torenvalk",
        "eurasian hobby" to "Boomvalk",
        "peregrine falcon" to "Slechtvalk",
        "western osprey" to "Visarend",
        "white-tailed eagle" to "Zeearend",
        "red kite" to "Rode Wouw",
        "black kite" to "Zwarte Wouw",
        "western marsh harrier" to "Bruine Kiekendief",
        "hen harrier" to "Blauwe Kiekendief",
        "montagu's harrier" to "Grauwe Kiekendief",
        "eurasian spoonbill" to "Lepelaar",
        "gray heron" to "Blauwe Reiger",
        "grey heron" to "Blauwe Reiger",
        "great egret" to "Grote Zilverreiger",
        "little egret" to "Kleine Zilverreiger",
        "white stork" to "Ooievaar",
        "black stork" to "Zwarte Ooievaar",
        "mallard" to "Wilde Eend",
        "gadwall" to "Krakeend",
        "eurasian wigeon" to "Smient",
        "eurasian teal" to "Wintertaling",
        "garganey" to "Zomertaling",
        "northern shoveler" to "Slobeend",
        "northern pintail" to "Pijlstaart",
        "tufted duck" to "Kuifeend",
        "common pochard" to "Tafeleend",
        "common goldeneye" to "Brilduiker",
        "smew" to "Nonnetje",
        "goosander" to "Grote Zaagbek",
        "red-breasted merganser" to "Middelste Zaagbek",
        "great cormorant" to "Aalscholver",
        "eurasian coot" to "Meerkoet",
        "common moorhen" to "Waterhoen",
        "common crane" to "Kraanvogel",
        "eurasian curlew" to "Wulp",
        "whimbrel" to "Regenwulp",
        "black-tailed godwit" to "Grutto",
        "bar-tailed godwit" to "Rosse Grutto",
        "ruddy turnstone" to "Steenloper",
        "red knot" to "Kanoet",
        "sanderling" to "Drieteenstrandloper",
        "dunlin" to "Bonte Strandloper",
        "little stint" to "Kleine Strandloper",
        "common snipe" to "Watersnip",
        "jack snipe" to "Bokje",
        "eurasian woodcock" to "Houtsnip",
        "common greenshank" to "Groenpootruiter",
        "common redshank" to "Tureluur",
        "spotted redshank" to "Zwarte Ruiter",
        "wood sandpiper" to "Bosruiter",
        "green sandpiper" to "Witgat",
        "common sandpiper" to "Oeverloper",
        "black-headed gull" to "Kokmeeuw",
        "mew gull" to "Stormmeeuw",
        "common gull" to "Stormmeeuw",
        "herring gull" to "Zilvermeeuw",
        "lesser black-backed gull" to "Kleine Mantelmeeuw",
        "great black-backed gull" to "Grote Mantelmeeuw",
        "mediterranean gull" to "Zwartkopmeeuw",
        "little gull" to "Dwergmeeuw",
        "sandwich tern" to "Grote Stern",
        "common tern" to "Visdief",
        "arctic tern" to "Noordse Stern",
        "little tern" to "Dwergstern",
        "black tern" to "Zwarte Stern"
    )
}
