package com.yvesds.vt5.ai

/**
 * SpeciesGuildMapper - Wetenschappelijk onderlegde mapping voor Europese trekvogels.
 * Deelt soorten in op basis van hun Latijnse geslachtsnaam (Genus).
 */
object SpeciesGuildMapper {

    enum class FlightStrategy {
        THERMAL, // Zwevers (Ooievaar, Buizerd, Wespendief)
        ACTIVE,  // Actieve vliegers (Reigers, Kiekendieven, Valken, Steltlopers)
        VISMIG   // Visuele trek (Zangvogels, Zwaluwen, Sterns, Eenden, Kustvogels)
    }

    enum class Guild(val displayName: String, val isSpecial: Boolean, val strategy: FlightStrategy) {
        WATERFOWL("Watervogels (Ganzen/Grondeleenden)", false, FlightStrategy.VISMIG),
        COASTAL_BIRDS("Kustvogels (Zee-eenden/Duikers/Futen)", false, FlightStrategy.VISMIG),
        RAPTORS_THERMAL("Roofvogels (Zwevers)", false, FlightStrategy.THERMAL),
        RAPTORS_ACTIVE("Roofvogels (Actief)", false, FlightStrategy.ACTIVE),
        HERONS("Reigers", false, FlightStrategy.ACTIVE),
        STORKS("Ooievaars (Zwevers)", false, FlightStrategy.THERMAL),
        SHOREBIRDS("Steltlopers", false, FlightStrategy.ACTIVE),
        GULLS_TERNS("Meeuwen & Sterns", false, FlightStrategy.VISMIG),
        PELAGICS("Zeevogels (Pelagics)", false, FlightStrategy.VISMIG),
        LANDBIRDS_SPECIAL("Speciale Landvogels", true, FlightStrategy.VISMIG),
        LANDBIRDS_REG("Landvogels", false, FlightStrategy.VISMIG),
        PASSERINES("Zangvogels", false, FlightStrategy.VISMIG),
        UNCLASSIFIED_BIRDS("Overige Vogels", false, FlightStrategy.VISMIG),
        OTHER("Niet-vogels", false, FlightStrategy.VISMIG)
    }

    fun getGuildByLatin(latinName: String?): Guild {
        if (latinName.isNullOrBlank()) return Guild.UNCLASSIFIED_BIRDS
        val genus = latinName.split(" ")[0].trim()

        return when (genus) {
            // 1. Waterfowl (Anseriformes, Suliformes, Podicipediformes)
            "Anser", "Branta", "Cygnus", "Anas", "Spatula", "Mareca", "Netta", "Aythya", 
            "Tadorna", "Aix", "Alopochen", "Oxyura",
            "Phalacrocorax", "Microcarbo", // Aalscholvers (nu bij watervogels)
            "Podiceps", "Tachybaptus"      // Futen (nu bij watervogels)
            -> Guild.WATERFOWL

            // 2. Coastal Birds (Sea Ducks, Divers)
            "Somateria", "Melanitta", "Clangula", "Bucephala", "Mergellus", "Mergus", "Polysticta", "Histrionicus",
            "Gavia"
            -> Guild.COASTAL_BIRDS

            // 3. Raptors - Thermal
            "Buteo", "Pernis", "Aquila", "Hieraaetus", "Clanga", "Haliaeetus", "Milvus", "Gyps", "Gypaetus", "Neophron", "Aegypius", "Circaetus", "Pandion" 
            -> Guild.RAPTORS_THERMAL

            // 4. Raptors - Active
            "Circus", "Accipiter", "Falco", "Elanus" 
            -> Guild.RAPTORS_ACTIVE

            // 5. Herons (Active)
            "Ardea", "Egretta", "Bubulcus", "Ardeola", "Nycticorax", "Ixobrychus", "Botaurus", "Platalea", "Plegadis", "Threskiornis" 
            -> Guild.HERONS

            // 6. Storks (Thermal)
            "Ciconia" -> Guild.STORKS

            // 7. Shorebirds
            "Haematopus", "Himantopus", "Recurvirostra", "Burhinus", "Cursorius", "Glareola", "Vanellus", "Pluvialis", 
            "Charadrius", "Numenius", "Limosa", "Arenaria", "Calidris", "Scolopax", "Gallinago", "Lymnocryptes", 
            "Phalaropus", "Actitis", "Tringa", "Gallinula", "Fulica", "Grus", "Porzana", "Zapornia", "Crex", "Rallus" 
            -> Guild.SHOREBIRDS

            // 8. Gulls & Terns
            "Rissa", "Pagophila", "Xema", "Chroicocephalus", "Larus", "Ichthyaetus", "Hydrocoloeus", 
            "Gelochelidon", "Hydroprogne", "Thalasseus", "Sterna", "Sternula", "Chlidonias", "Onychoprion" 
            -> Guild.GULLS_TERNS

            // 9. Pelagics (True open sea birds)
            "Fulmarus", "Puffinus", "Calonectris", "Hydrobates", "Oceanodroma", "Morus", "Stercorarius", "Uria", "Alca", "Fratercula", "Ardenna" 
            -> Guild.PELAGICS

            // 10. Landbirds Special
            "Merops", "Upupa", "Coracias", "Alcedo", "Cuculus", "Caprimulgus", "Jynx", "Dendrocopos", "Dryocopus", "Picus", "Oriolus", "Lanius", "Picoides", "Dryobates" 
            -> Guild.LANDBIRDS_SPECIAL

            // 11. Landbirds Regular
            "Columba", "Streptopelia", "Apus", "Hirundo", "Delichon", "Riparia", "Ptyonoprogne", "Cecropis" 
            -> Guild.LANDBIRDS_REG

            // 12. Passerines
            "Fringilla", "Carduelis", "Chloris", "Spinus", "Linaria", "Acanthis", "Loxia", "Pyrrhula", "Coccothraustes", "Serinus",
            "Emberiza", "Calcarius", "Plectrophenax", "Passer", "Anthus", "Motacilla", "Alauda", "Lullula", "Galerida", "Sturnus", "Pastor", 
            "Turdus", "Luscinia", "Erithacus", "Phoenicurus", "Saxicola", "Oenanthe", "Muscicapa", "Ficedula", "Sylvia", "Curruca", 
            "Phylloscopus", "Acrocephalus", "Iduna", "Hippolais", "Locustella", "Cettia", "Parus", "Cyanistes", "Periparus", "Lophophanes", 
            "Poecile", "Aegithalos", "Sitta", "Certhia", "Troglodytes", "Cinclus", "Regulus", "Panurus", "Corvus", "Coloeus", "Pica", "Garrulus", 
            "Nucifraga", "Pyrrhocorax", "Remiz", "Bombycilla", "Carpodacus" 
            -> Guild.PASSERINES

            // Insecten & Zoogdieren filters
            "Sympetrum", "Aeshna", "Libellula", "Anax", "Somatochlora", "Cordulia", "Pieris", "Vanessa", "Aglais", "Inachis", "Colias" -> Guild.OTHER
            "Delphinus", "Tursiops", "Phocoena", "Phoca", "Halichoerus", "Globicephala", "Orcinus" -> Guild.OTHER

            else -> Guild.UNCLASSIFIED_BIRDS
        }
    }

    /**
     * Fallback mapping gebaseerd op soortid indien latin ontbreekt.
     */
    fun getGuild(soortid: String): Guild {
        return Guild.UNCLASSIFIED_BIRDS
    }
}
