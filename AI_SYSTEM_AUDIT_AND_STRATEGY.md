# VoiceTally (VT5) AI Systeem: Audit & Toekomststrategie

> [!IMPORTANT]
> **Anchor Point (Herstelpunt)**: `ai-base-anchor` (Commit: `dc3dbbc`)
> Gebruik `git checkout ai-base-anchor` om terug te keren naar de stabiele basis van vóór de strategische uitbreidingen.

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

## DEEL 2: Strategische Verbeteringen (Geïmplementeerd ✅)

De volgende features zijn nu toegevoegd aan de data-extractie en de inference-engine:

### 1. Barometrische Tendens ✅
*   **Implementatie**: De `AiWeatherService` haalt nu de luchtdruk van 6 uur geleden op via Open-Meteo. Het verschil (stijgend/dalend) wordt als feature `pressure_trend` meegegeven.

### 2. Maanfase ✅
*   **Implementatie**: Wiskundige berekening (`calculateMoonPhase`) toegevoegd aan `TrainingDataPreparer` en `AiInferenceEngine` op basis van de synodische maand (29.53 dagen). Feature: `moon_phase`.

### 3. Wind-chill & "Gevoelstemperatuur" ✅
*   **Implementatie**: Steadman-formule geïmplementeerd om de impact van wind op de temperatuur te berekenen. Feature: `wind_chill`.

### 4. Dynamische Sample Weighting & Rarity ✅
*   **Implementatie**: De AI kijkt nu naar de relatieve schaarste van een soort in de database (< 0.1% van totaal = zeldzaam). Feature: `is_rare`.

### 5. Gegevens van "Gisteren" (Trekstroom) ✅
*   **Implementatie**: SQL-query toegevoegd die de totale aantallen van de afgelopen 24 uur aggregeert. Feature: `yesterday_count`.

---

## Conclusie & Volgende Stappen
De architectuur is nu klaar voor een "straffere" training. Omdat het aantal variabelen is uitgebreid van 2 naar 19 actieve features, **moet er een nieuwe AI-Update worden uitgevoerd** via de app om een compatibel `personal_migration_model.tflite` te genereren.
