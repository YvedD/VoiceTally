package com.yvesds.vt5.ai

/**
 * SpeciesGuildMapper - Koppelt Trektellen soortid's aan biologische gilden.
 * Geoptimaliseerd voor de Europese vogelmigratie.
 */
object SpeciesGuildMapper {

    enum class Guild(val displayName: String) {
        PASSERINES("Zangvogels"),
        RAPTORS("Roofvogels"),
        WADERS_LONG("Reigers & Ooievaars"),
        WATERFOWL("Watervogels"),
        SHOREBIRDS("Steltlopers"),
        PELAGICS("Zeevogels (Pelagics)"),
        LARIDAE("Meeuwen & Sterns"),
        LANDBIRDS("Landvogels (o.a. Duiven)"),
        OTHER("Overig")
    }

    private val mapping = mapOf(
        // Passerines (Zangvogels)
        "401" to Guild.PASSERINES, "402" to Guild.PASSERINES, "307" to Guild.PASSERINES,
        "306" to Guild.PASSERINES, "309" to Guild.PASSERINES, "278" to Guild.PASSERINES,
        "279" to Guild.PASSERINES, "362" to Guild.PASSERINES, "361" to Guild.PASSERINES,
        "316" to Guild.PASSERINES, "317" to Guild.PASSERINES, "318" to Guild.PASSERINES,
        "352" to Guild.PASSERINES, "353" to Guild.PASSERINES, "304" to Guild.PASSERINES,
        "320" to Guild.PASSERINES, // Gele Kwik
        "308" to Guild.PASSERINES, // Duinpieper

        // Raptors (Roofvogels)
        "96" to Guild.RAPTORS, "97" to Guild.RAPTORS, "98" to Guild.RAPTORS,
        "95" to Guild.RAPTORS, "90" to Guild.RAPTORS, "113" to Guild.RAPTORS,
        "112" to Guild.RAPTORS, "87" to Guild.RAPTORS, "100" to Guild.RAPTORS,
        "99" to Guild.RAPTORS, "102" to Guild.RAPTORS, "92" to Guild.RAPTORS,
        "93" to Guild.RAPTORS, "108" to Guild.RAPTORS, "110" to Guild.RAPTORS, // Torenvalk

        // Long-legged Waders (Reigers & Ooievaars)
        "27" to Guild.WADERS_LONG, "26" to Guild.WADERS_LONG, "31" to Guild.WADERS_LONG,
        "32" to Guild.WADERS_LONG, "33" to Guild.WADERS_LONG, "1001" to Guild.WADERS_LONG,
        "123" to Guild.WADERS_LONG, "28" to Guild.WADERS_LONG, "25" to Guild.WADERS_LONG,

        // Waterfowl (Watervogels)
        "56" to Guild.WATERFOWL, "59" to Guild.WATERFOWL, "61" to Guild.WATERFOWL,
        "60" to Guild.WATERFOWL, "48" to Guild.WATERFOWL, "49" to Guild.WATERFOWL,
        "41" to Guild.WATERFOWL, "42" to Guild.WATERFOWL, "38" to Guild.WATERFOWL,
        "84" to Guild.WATERFOWL, "1" to Guild.WATERFOWL, "2" to Guild.WATERFOWL,
        "76" to Guild.WATERFOWL, "71" to Guild.WATERFOWL,

        // Shorebirds (Steltlopers)
        "173" to Guild.SHOREBIRDS, "174" to Guild.SHOREBIRDS, "159" to Guild.SHOREBIRDS,
        "176" to Guild.SHOREBIRDS, "177" to Guild.SHOREBIRDS, "171" to Guild.SHOREBIRDS,
        "172" to Guild.SHOREBIRDS, "143" to Guild.SHOREBIRDS, "142" to Guild.SHOREBIRDS,
        "161" to Guild.SHOREBIRDS, "152" to Guild.SHOREBIRDS,

        // Pelagics (Zeevogels)
        "19" to Guild.PELAGICS, "11" to Guild.PELAGICS, "12" to Guild.PELAGICS,
        "225" to Guild.PELAGICS, "226" to Guild.PELAGICS, "16" to Guild.PELAGICS,
        "7" to Guild.PELAGICS, "14" to Guild.PELAGICS, "15" to Guild.PELAGICS,

        // Laridae (Meeuwen & Sterns)
        "227" to Guild.LARIDAE, "232" to Guild.LARIDAE, "231" to Guild.LARIDAE,
        "234" to Guild.LARIDAE, "233" to Guild.LARIDAE, "222" to Guild.LARIDAE,
        "237" to Guild.LARIDAE, "235" to Guild.LARIDAE, "221" to Guild.LARIDAE,

        // Landbirds (Landvogels / Overig)
        "233" to Guild.LANDBIRDS, // Houtduif
        "234" to Guild.LANDBIRDS, // Holenduif
        "236" to Guild.LANDBIRDS, // Turkse Tortel
        "235" to Guild.LANDBIRDS, // Zomertortel
        "251" to Guild.LANDBIRDS, // Koekoek
        "731" to Guild.LANDBIRDS, // Gierzwaluw
        "738" to Guild.LANDBIRDS  // Hop
    )

    fun getGuild(soortid: String): Guild {
        return mapping[soortid] ?: Guild.OTHER
    }
}
