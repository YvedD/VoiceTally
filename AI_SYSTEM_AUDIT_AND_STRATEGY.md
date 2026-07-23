# VoiceTally (VT5) AI Systeem: Audit & Toekomststrategie

Dit document bevat een gedetailleerde audit van het huidige AI-systeem en een strategisch plan voor het verder versterken van de voorspellingen zonder de app zwaar te belasten.

---

## DEEL 1: Audit van het Huidige Systeem

### 1. Input Features (Waar kijkt de AI naar?)
De AI baseert zijn voorspellingen op **20 wiskundige variabelen**:
*   **Cyclische Tijd**: Uur van de dag en dag van het jaar (Sinus/Cosinus transformatie).
*   **Locatie**: De unieke `siteid` van de telpost.
*   **Lokaal Weer**: Temperatuur, windsnelheid, windrichting (vectoren), bewolking, zichtbaarheid en neerslag.
*   **Regionaal Weer**: Weersomstandigheden op strategische referentiepunten in Europa (Noord-Frankrijk/Duitsland) om de trekdruk te bepalen.

### 2. Intelligentie & Verbanden
*   **Correlaties**: Leert welke windrichtingen specifiek op jouw post voor welke soorten zorgen.
*   **Seizoensvensters**: Herkent de exacte biologische piekperiodes per soort.
*   **Trek-stuw-effect**: Analyseert of weer op afstand de trek richting jouw post blokkeert of juist stimuleert.

### 3. Zeldzame Soorten vs. Massa
*   **Sample Weighting**: Maakt gebruik van een weegfactor (bijv. 250). Eén zeldzame soort telt voor het model net zo zwaar als 250 vinken, waardoor zeldzaamheden niet "verdrinken" in de statistiek.

### 4. Zelftraining & Opslag
*   **On-Device Training**: Het model traint lokaal op de smartphone via de `Trainer` klasse.
*   **Opslag**: Gebruikt een ingebakken `base_model.tflite` als startpunt en bouwt een `personal_migration_model.tflite` op de externe opslag.
*   **Feedback Loop**: Gebruikersbeoordelingen worden opgeslagen als JSON voor toekomstige modelcorrecties.

---

## DEEL 2: Strategische Verbeteringen (De "Straffere" AI)

Om de voorspellingen nog nauwkeuriger te maken zonder de app zwaarder te maken, kunnen we de volgende features en logica toevoegen aan de data-extractie:

### 1. Barometrische Tendens (Trend-analyse)
*   **Idee**: Niet alleen kijken naar de huidige luchtdruk, maar naar het verschil met 6 of 12 uur geleden.
*   **Impact**: Een plotselinge daling van luchtdruk voorspelt vaak de aankomst van een front, wat een enorme impact heeft op vogelgedrag.
*   **Implementatie**: De Open-Meteo API levert historische data; we kunnen de trend (stijgend/dalend) als extra feature meegeven.

### 2. Maanfase & Nachtelijke Trek
*   **Idee**: Voor soorten die 's nachts trekken en 's ochtends landen (zoals lijsters en goudhaantjes), is de maanfase en de helderheid van de nacht cruciaal.
*   **Impact**: Volle maan bij heldere hemel bevordert nachtelijke trek.
*   **Implementatie**: Maanfase is puur rekenkundig en kost bijna geen rekenkracht.

### 3. "Arrival vs. Passage" Logica
*   **Idee**: De AI onderscheid laten maken tussen vogels die 'overvliegen' en vogels die 'landen/foerageren'.
*   **Impact**: Verbetering van suggesties bij mist of regen (wanneer vogels sneller aan de grond zitten).

### 4. Dynamische Sample Weighting (Automatisch)
*   **Idee**: De weegfactor voor zeldzame soorten niet hardcoden, maar laten afhangen van de schaarste in jouw specifieke database.
*   **Impact**: Als je 10 jaar lang geen Draaihals hebt gezien, wordt die ene waarneming automatisch extreem zwaar gewogen in het model.

### 5. Wind-chill & "Gevoelstemperatuur"
*   **Idee**: Gebruik de windchill-index in plaats van alleen de droge temperatuur.
*   **Impact**: Vogels reageren sterker op de energie-impact van koude wind dan op de thermometerstand.

### 6. Gegevens van "Gisteren" (Trekstroom)
*   **Idee**: Een feature toevoegen die aangeeft hoeveel vogels er gisteren (of de afgelopen 3 dagen) zijn geteld.
*   **Impact**: Trek komt vaak in golven. Als het gisteren "losbarstte" in de regio, is de kans op naloop vandaag groter.

---

## Conclusie
Door bovenstaande variabelen toe te voegen aan de `TrainingDataPreparer` en de input-vector van de AI, verhogen we de biologische relevantie van het model aanzienlijk. Omdat deze data direct uit de bestaande database en gratis weer-API's komt, blijft de app razendsnel.
