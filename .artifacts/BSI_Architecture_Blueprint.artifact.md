# BSI Model Architectuur: Blauwdruk voor Cloud Conversie (Python/Oracle)

Dit document beschrijft de volledige logica van het **Bio-Statistische Intelligentie (BSI)** model in de VoiceTally VT5 app. Gebruik dit als specificatie voor de conversie naar een Python-gebaseerde Cloud service.

---

## 1. Het Concept: Hybride Intelligentie
BSI is geen puur 'black-box' neuraal netwerk. Het is een hybride systeem:
1.  **Statistische Basis**: Historische trek-aantallen per soort per dag.
2.  **Neurale Abstractie**: Leert complexe relaties tussen weer-parameters (bijv. "stijgende luchtdruk + NO wind = vinkentrek").
3.  **Meteorologische Corridor-Analyse**: Monitort het weer op strategische Europese 'bron-locaties' om de instroom van vogels te voorspellen.
4.  **Lite Neural Engine (LNE)**: Een lichte Multi-Layer Perceptron (MLP) die patronen leert uit historische data.
5.  **Expert Rules (Guilds)**: Hard-coded biologische beperkingen (bijv. "buizerds trekken niet in de regen").

---

## 2. Strategische Corridors: De Europese Pijplijn

Vogels verschijnen niet uit het niets; hun trek wordt bepaald door weersomstandigheden honderden kilometers verderop. Het BSI-model monitort 21 strategische referentiepunten.

### 2.1 Referentiepunten (Corridor Locaties)

| Regio | Locatie | Coördinaten (Lat, Lon) | Functie |
| :--- | :--- | :--- | :--- |
| **NOORD** | Falsterbo (SE) | 55.38, 12.83 | Hoofdbron najaarstrek (Scandinavië) |
| | Skagen (DK) | 57.72, 10.58 | Bottleneck najaar |
| | Helgoland (DE) | 54.18, 7.88 | Graadmeter voor najaars-zeetrek |
| | Texel (NL) | 53.05, 4.80 | Voorpost voor de Belgische kust |
| | Gdansk (PL) | 54.35, 18.64 | Oostelijke najaarscorridor |
| **ZUID** | Cap Gris-Nez (FR) | 50.87, 1.58 | Belangrijkste voorjaar-bottleneck |
| | Tarifa (ES) | 36.01, -5.60 | Ingangspunt vanuit Afrika |
| | Gibraltar (UK) | 36.14, -5.35 | Ingangspunt vanuit Afrika |
| | Camargue (FR) | 43.53, 4.65 | Mediterrane voorjaarscorridor |
| | Biarritz (FR) | 43.48, -1.56 | Atlantische voorjaarscorridor |

*(Opmerking: De volledige lijst in `AiConfig.kt` bevat 21 punten waaronder ook Ouistreham, Mallorca en Messina).*

### 2.2 Waartoe dienen deze locaties?
Het model gebruikt deze locaties om **"Meteorologische Drukgradiënten"** te berekenen:
1.  **Bron-condities**: Als het in Falsterbo perfect trekweer is (rugwind, helder) maar lokaal nog niet, voorspelt BSI een stijging voor de komende 24-48 uur (de "reistijd").
2.  **Stuwing**: Harde wind op deze locaties kan vogels uit hun normale koers drukken richting de telpost van de gebruiker (bijv. NW storm op Helgoland stuwt zeevogels naar de Belgische kust).
3.  **Blokkades**: Slecht weer (regenfronten) op deze locaties fungeert als een "dam". Zodra de dam doorbreekt (het weer klaart op), berekent BSI een enorme trek-impuls.

### 2.3 Gebruik in de BSI-Prognose
In de Python-engine moet deze data als volgt verwerkt worden:
-   **Time-Lagging**: Het weer op NOORD-punten wordt met een vertraging (lag) van 12, 24 en 36 uur meegewogen voor de najaarsprognose.
-   **Vector-Analyse**: De windrichting tussen een referentiepunt en de telpost wordt geanalyseerd. Is er een "open corridor" (gunstige wind over de hele lijn)? Dan krijgt de `NeuralScore` een multiplicatieve boost.

---

## 3. Data-Infrastructuur

### 2.1 Corridors (35km Clusters)
**Waarom**: Weersomstandigheden zijn lokaal. Een telpost aan de kust heeft een ander profiel dan een post in het binnenland.
**Logica**:
- De app groepeert telposten in clusters met een straal van **35 km**.
- De eerste telpost in een regio is de 'Anchor' (Hoofdtelpost).
- Alle data-analyses (piekdagen, gemiddelden) worden uitgevoerd op cluster-niveau.
- **Python Vertaling**: Gebruik de `Haversine` formule om telposten te groeperen in SQL of Python.

### 2.2 Essentiële JSON Bestanden
Voor een correcte werking van de engine in Python zijn de volgende bestanden uit de `VT5/` SAF-structuur noodzakelijk:

| Bestandsnaam | Locatie | Rol in BSI |
| :--- | :--- | :--- |
| **`sites.json`** | `serverdata/` | Lijst van alle telposten en hun metadata. |
| **`species.json`** | `serverdata/` | Basislijst met soort-ID's, namen en wetenschappelijke namen. |
| **`telpost_locaties.json`** | `serverdata/` | **Cruciaal**: Bevat coördinaten (Lat/Lon) voor corridor-berekening en weer-data. |
| **`site_species.json`** | `serverdata/` | Koppeling tussen telposten en verwachte soorten (filtering). |
| **`codes.json`** | `serverdata/` | Vertaling van weer-codes en tel-types. |
| **`neural_engine.json`** | `AI-models/models/` | De geëxporteerde gewichten en biassen van het getrainde netwerk. |
| **`model_labels.json`** | `AI-models/models/` | Mapping van output-neuronen naar `soortid`. |
| **`expert_knowledge.json`** | `AI-models/models/` | Meteorologische vingerafdrukken voor de krenten-monitor. |

### 2.3 Database Tabellen (SQLite)
De belangrijkste bronnen voor training (beschikbaar via de 'Spiegel' knop):
- `telling_headers`: Bevat context (telpostid, begintijd, onlineid).
- `waarnemingen`: De werkelijke resultaten (soortid, aantal).
- `weather_archive`: Uur-precies weer gekoppeld aan `tellingid`.
- `species_phenology_vault`: Gecomprimeerde trek-curves per soort.

---

## 3. Weergegevens: Acquisitie en Verwerking

Het model is volledig afhankelijk van nauwkeurige meteorologische data. De app gebruikt twee bronnen en twee verschillende strategieën (Live/Forecast vs. Archief).

### 3.1 Bronnen en Protocollen
De app communiceert met Open-Meteo via HTTP GET requests.

#### A. Forecast & Live Data (Real-time & Trend)
Gebruikt voor de huidige telsessie en de 6-uurs druk-trend.
-   **Base URL**: `https://api.open-meteo.com/v1/forecast`
-   **Parameters**:
    -   `latitude`, `longitude`: Coördinaten van de telpost.
    -   `hourly=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,precipitation,visibility`
    -   `past_days=1`: Noodzakelijk voor de berekening van de trend t.o.v. gisteren.
    -   `forecast_days=3`: Voor de +3 dagen vooruitblik.
-   **Voorbeeld Request**:
    `https://api.open-meteo.com/v1/forecast?latitude=51.2&longitude=4.4&hourly=pressure_msl,temperature_2m&past_days=1&forecast_days=1`

#### B. Historisch Archief (Training & Enrichment)
Gebruikt voor het vullen van de `weather_archive` tabel.
-   **Base URL**: `https://archive-api.open-meteo.com/v1/archive`
-   **Parameters**:
    -   `start_date`, `end_date`: In formaat `YYYY-MM-DD`.
    -   `hourly=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,precipitation`
    -   `wind_speed_unit=ms`: De app rekent intern met meters per seconde (daarna pas naar Bft).
    -   `timezone=UTC`: Standaardisatie om database-consistentie te garanderen.
-   **Voorbeeld Request**:
    `https://archive-api.open-meteo.com/v1/archive?latitude=51.2&longitude=4.4&start_date=2023-01-01&end_date=2023-12-31&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,precipitation&wind_speed_unit=ms&timezone=UTC`

### 3.2 Gebruikte Variabelen (Features)
Voor elk uur worden de volgende parameters opgehaald:
-   `temperature_2m` (°C)
-   `wind_speed_10m` (omgezet naar **Beaufort**)
-   `wind_direction_10m` (omgezet naar **16 windsectoren**: N, NNO, NO, etc.)
-   `pressure_msl` (hPa)
-   `cloud_cover` (%)
-   `precipitation` (mm)
-   `visibility` (meters)
-   **Druk-Trend**: Het verschil in hPa tussen 'nu' en 6 uur geleden (`pressure_msl_now - pressure_msl_6h`). Dit is de krachtigste voorspeller voor "trek-uitbraken".

### 3.3 De +3 Dagen Prognose Logica
Bij een vooruitblik (bijv. +3 dagen) voert de engine de volgende stappen uit:
1.  **Uur-data ophalen**: De app vraagt de hourly-forecast op voor de coördinaten van de telpost voor de komende 72 uur.
2.  **Feature Mapping**: Elk uur wordt omgezet in een feature-vector (zie sectie 5).
3.  **Neural Inference**: De `neural_engine.json` (MLP) berekent per uur de activatie van elke vogelsoort.
4.  **Guild/Trek-curve correctie**: De neurale score wordt vermenigvuldigd met de biologische waarschijnlijkheid (is de vogel in deze periode in het land?) en de Guild-regels (bijv. is het uur geschikt voor thermiek?).

## 5. Weergegevens Verwerking & Database Integratie

Het verwerken van meteorologische data van Open-Meteo naar de Room database gebeurt volgens een strikt protocol om prestaties en data-integriteit te garanderen.

### 5.1 JSON Parsing Strategie
De Open-Meteo API retourneert data in een "kolom-georiënteerd" formaat in plaats van een lijst met objecten. Dit is extreem efficiënt voor bandbreedte maar vereist specifieke verwerking:
-   **JSON Structuur**: Bevat een object `hourly` met parallelle arrays: `time` (strings), `temperature_2m` (doubles), etc.
-   **Verwerkings-loop**:
    1.  De app itereert over de indexen van de `time` array.
    2.  Elke ISO-8601 tijdstempel (bijv. `"2023-01-01T08:00"`) wordt geparsed naar **Unix Epoch Seconds** (UTC).
    3.  Met dezelfde index `i` worden de waarden uit de andere arrays opgehaald (wind, druk, etc.).
-   **Python Tip**: Gebruik `pandas.DataFrame.from_dict(json_data['hourly'])` voor een instant conversie naar een tabel-formaat.

### 5.2 Database Batching & Throttling
Om de SQLite database (en later de Oracle Cloud DB) niet te verstikken, worden de volgende technieken gebruikt:
1.  **Batch Inserts**: Records worden niet één voor één opgeslagen. De app verzamelt **1000 records** in het geheugen en voert dan een `INSERT OR IGNORE` operatie uit in één transactie.
2.  **Dispatcher Breathing (Time-outs)**:
    -   Tijdens het bijwerken van duizenden headers gebruikt de app een `delay(1)` (een micro-pauze van 1 milliseconde).
    -   **Doel**: Dit geeft de CPU/Dispatcher de kans om andere taken (zoals UI-updates) af te handelen, waardoor de app niet "bevriest" tijdens de zware berekeningen.
3.  **Sequential Cluster Loading**: API-aanroepen voor verschillende 35km clusters worden sequentieel (na elkaar) uitgevoerd. Dit voorkomt dat de app de Open-Meteo API overbelast met gelijktijdige requests, wat tot "429 Too Many Requests" errors zou leiden.

### 5.3 Data-Integriteit
-   **Primary Key**: De tabel `weather_archive` gebruikt een samengestelde sleutel van `locationId` + `timeEpoch`. Dit voorkomt dubbele weersgegevens voor hetzelfde uur op dezelfde plek.
-   **Timezone**: De app vraagt altijd `timezone=UTC` op bij Open-Meteo. Dit zorgt ervoor dat alle tijden in de database gestandaardiseerd zijn, onafhankelijk van waar de telpost zich fysiek bevindt.

---

## 6. Feature Engineering (De AI Input)
Voordat de Neurale Engine berekeningen doet, bouwt de `TrainingDataPreparer` een **Feature Vector** (een array van floats):

1.  **Dag-circulariteit**: `sin(2π * day/365)` en `cos(2π * day/365)`. Dit zorgt dat 31 dec en 1 jan dicht bij elkaar liggen.
2.  **Zon-fase**: Een waarde 0.0 t/m 1.0 op basis van `SolarTimeEngine`:
    - Nacht: 0.0
    - Dageraad: 0.3
    - Dag: 1.0
    - Schemering: 0.5
3.  **Meteorologische variabelen**: Genormaliseerd weer (Temp, Windkracht, Bewolking, Neerslag).
4.  **Druk-impuls**: De 3-uurs trend (cruciaal voor het voorspellen van 'uitbraken' na slecht weer).

---

## 5. De Lite Neural Engine (LNE)
Dit is een Multi-Layer Perceptron (MLP) geïmplementeerd in pure Kotlin.

### 5.1 Architectuur
- **Input Layer**: ~12 features (weer + tijd).
- **Hidden Layer**: 24-32 neuronen (instelbaar).
- **Output Layer**: 1 neuron per vogelsoort (geeft een activatie-waarde tussen 0.0 en 1.0).
- **Activatie Functie**: `Sigmoid` of `ReLU`.

### 5.2 Training (Het leerproces)
- De app draait `Backpropagation` op de smartphone.
- Het 'label' (doelwit) is het aantal vogels van soort X, geschaald naar een waarde tussen 0 en 1 (meestal via een logaritmische schaal om uitschieters te temperen).

---

## 6. Guild-gebaseerde Interpretatie
Na de neurale pass wordt een 'Expert Filter' toegepast (`SpeciesGuildMapper.kt`).

| Guild | Kenmerken | BSI Correctie Factor |
| :--- | :--- | :--- |
| **THERMAL** | Buizerd, Ooievaar | Penalty bij wind > 4 Bft of regen. Bonus bij hoge temp. |
| **ACTIVE** | Vinken, Piepers | Bonus bij vroege ochtend + lichte tegenwind. |
| **PELAGICS** | Zeevogels | Bonus bij storm (> 6 Bft) uit NW richtingen. |
| **VISMIG** | Duiven, Spreeuwen | Neurale dominantie (volgen strakke tijdslijnen). |

---

## 7. Prognose Berekening (De Finale Stap)
Voor elk uur wordt een score berekend:
1.  **Neural Score**: Wat 'denkt' het model op basis van het weer?
2.  **Phenology Score**: Is de soort überhaupt in het land volgens de trek-curve?
3.  **Combining**: `FinalScore = NeuralScore * PhenologyScore * GuildMultiplier`.

De top-soorten worden gesorteerd op `FinalScore` en gepresenteerd in de UI.

---

## 9. DAO & SQL Logica: De Data-pijplijn
De volgende SQLite queries zijn de levensaders van het model. Ze transformeren ruwe waarnemingen naar AI-vriendelijke datasets.

### 9.1 Training Data Extractie
Deze query koppelt sessies aan waarnemingen om de basis-training dataset te bouwen.
```sql
SELECT
    w.soortid,
    h.begintijd as sessionStart,
    w.tijdstip as observationTime,
    h.windrichting,
    h.windkracht,
    h.temperatuur,
    h.bewolking,
    h.hpa,
    h.neerslag,
    h.telpostid
FROM waarnemingen w
INNER JOIN telling_headers h ON w.tellingid = h.tellingid
WHERE h.status = 'gearchiveerd' OR h.status = 'geupload'
ORDER BY h.begintijd DESC
```

### 9.2 Fenologisch Profiel (Per Soort)
Berekent de meteorologische voorkeuren van een soort binnen een specifiek datumbereik.
```sql
SELECT
    w.soortid,
    SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) as totalCount,
    AVG(CAST(NULLIF(h.temperatuur, '') AS FLOAT)) as avgTemp,
    UPPER(h.windrichting) as mainWind,
    AVG(CAST(NULLIF(h.windkracht, '') AS FLOAT)) as avgBft,
    AVG(CAST(NULLIF(h.hpa, '') AS FLOAT)) as avgPressure,
    AVG(CAST(strftime('%H', datetime(CAST(MAX(w.tijdstip, h.begintijd) AS INTEGER), 'unixepoch', 'localtime')) AS INTEGER)) as avgHour
FROM waarnemingen w
INNER JOIN telling_headers h ON w.tellingid = h.tellingid
WHERE (CAST(strftime('%j', datetime(h.begintijd, 'unixepoch')) AS INTEGER) BETWEEN :dayStart AND :dayEnd)
GROUP BY w.soortid
```

### 9.3 Giga-Baseline (Cluster Intelligentie)
Bepaalt welke soorten dominant zijn in een specifieke corridor, gecorrigeerd voor de werkelijke tel-inspanning (`totalHours`).
```sql
SELECT
    w.soortid,
    SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) / :totalHours as clusterIndex,
    (SELECT COUNT(DISTINCT telpostid) FROM telling_headers WHERE telpostid IN (:siteIds)) as activePosts
FROM waarnemingen w
INNER JOIN telling_headers h ON w.tellingid = h.tellingid
WHERE h.telpostid IN (:siteIds)
GROUP BY w.soortid
```

### 9.4 Meteorologische Fingerprinting (Krenten-detectie)
Zoekt naar historische piekdagen voor een vogel-gilde om de huidige dag mee te vergelijken.
```sql
SELECT
    (CAST(h.begintijd AS INTEGER) / 86400) * 86400 as dayEpoch,
    SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) as totalCount
FROM waarnemingen w
INNER JOIN telling_headers h ON w.tellingid = h.tellingid
WHERE w.soortid IN (:guildSpeciesIds)
GROUP BY dayEpoch
ORDER BY totalCount DESC
LIMIT 5
```

---

## 11. UI & Visualisatie: De Prognose CardView

In de WebUI moeten de prognoses gepresenteerd worden in een overzichtelijke CardView. Hieronder staat exact gespecificeerd wat er getoond wordt en hoe de data hiervoor berekend wordt.

### 11.1 Onderdelen van de CardView
Elke vogelsoort in de prognose krijgt een kaart met de volgende elementen:
-   **Header**: De naam van de 'Guild' (Gilde), bijv. "Roofvogels (Thermiektrekkers)". De randkleur van de kaart matcht met de gilde-kleur (bijv. Groen voor Reigers, Rood voor Roofvogels).
-   **Naam**: Grote weergave van de Nederlandse soortnaam.
-   **Wetenschappelijke Info**: Latijnse naam + de berekende **BSI Kans** en **Norm-score**.
-   **BSI Kans (%)**: De kans dat de vogel aanwezig is, gebaseerd op het neurale model en filters.
-   **Norm Score (ex/h)**: Het historisch gemiddelde aantal exemplaren per uur voor deze telpost/regio.
-   **Foto**: Een thumbnail van de vogelsoort (indien beschikbaar in de cache).
-   **Historische Pieken**: Tekstuele weergave van de top-periodes, bijv. `[15 apr - 5 mei] [1 okt - 20 okt]`.
-   **Fenologie Grafiek**: Een sparkline die de trek-intensiteit over 52 weken toont.

### 11.2 Berekening: De BSI Score (Kans %)
De uiteindelijke kans (0-100%) is een aggregatie van 10 factoren:
1.  **F1 (Massa)**: Logaritmische schaal van het totaal aantal historische waarnemingen.
2.  **F2 (Wind-DNA)**: Vergelijking tussen de huidige windrichting en de ideale windrichting voor deze soort (tolerantie van 5.25 graden).
3.  **F3 (Specials)**: Bonus voor 'Krenten' of zeldzame soorten uit de Expert KB.
4.  **F4 (Tijd/Strategie)**: Match met de 'Gouden Mal' (uur-profiel) en de biologische vliegstrategie.
5.  **F5 (Gatekeeper)**: De 'Veto'-logica (bijv. landvogels trekken niet bij harde aanlandige wind aan de kust).
6.  **Efficiency Ratio**: Hoe de huidige wind presteert t.o.v. de 'all-time best' wind voor deze soort.
7.  **Corridor Boost**: Instroom vanuit de Europese referentiepunten (zie Sectie 2).
8.  **Daily Weight**: Correctie op basis van recent gereconstrueerde teldagen.
9.  **Neural Boost**: De 0.0-1.0 activatie vanuit de `LiteNeuralEngine`.
10. **Feedback Correctie**: Handmatige gebruikersbeoordelingen uit `user_evaluations.json`.

**Formule**: `FinalScore = F1 * F2 * F3 * F4 * F5 * Efficiency * Corridor * DailyW * NeuralFactor * Feedback`
De `FinalScore` wordt geschaald naar een percentage (max 98%).

### 11.3 Berekening: De Norm Score
De **Norm** (`expectedIndex`) is de 'Giga-Baseline' van een soort.
-   **Formule**: `Norm = Totaal_Aantal_Regio / Totale_Teltijd_Uren`.
-   Dit getal representeert de "verwachte druk" onder gemiddelde omstandigheden.

### 11.4 Historische Pieken (Voor- en Najaar)
De pieken worden berekend op basis van de dagelijkse distributie (`getSpeciesDailyDistribution`):
1.  Verdeel het jaar in **Voorjaar** (dag 1-166) en **Najaar** (dag 167-365).
2.  Zoek per periode de dag met het hoogste aantal (`maxCount`).
3.  Bepaal een venster rond deze dag waarbij de aantallen boven **50% van de maxCount** blijven.
4.  Dit venster wordt getoond als de "Historische Piek".

### 11.5 De Fenologie Grafiek (Sparkline)
De grafiek wordt getekend met de volgende logica:
-   **Data**: Een buffer van **54 punten** (week 0 t/m 53). Elke week zonder data krijgt waarde `0.0`.
-   **Rendering**: Een vloeiende 'Cubic Bezier' curve.
-   **Indicator**: Een verticale lijn (Date Indicator) die precies op de huidige week van het jaar staat. Zo ziet de gebruiker in één oogopslag of de huidige datum in een piek- of dalperiode valt.

### 11.6 Bronnen voor de Prognose
Bij het genereren van een "+3 dagen" prognose combineert de engine:
1.  **Lokaal Weer**: Forecast data van Open-Meteo voor de telpost.
2.  **Corridor Weer**: Forecast data van de 21 Europese referentiepunten.
3.  **Statische Kennis**: `species.json`, `site_species.json` en de `expert_knowledge.json`.
4.  **Dynamische Modellen**: De getrainde gewichten uit `neural_engine.json`.

---

## 12. Room Database Structuur: Datamodel Specificaties

Dit hoofdstuk beschrijft de exacte structuur van de SQLite database (Room). De tabel- en veldnamen zijn cruciaal voor de SQL-queries in de Python-engine.

### 12.1 Tabel: `telling_headers`
**Doel**: Bevat de metadata van elke telsessie.
- **Primary Key**: `tellingid` (TEXT)
- **Velden**:
    - `onlineid` (TEXT): ID op de centrale server/Excel.
    - `telpostid` (TEXT): Uniek ID van de locatie.
    - `begintijd`, `eindtijd` (TEXT): Epoch seconden (UTC).
    - `windrichting`, `windkracht`, `temperatuur`, `bewolking`, `hpa`, `neerslag` (TEXT).
    - `status` (TEXT): "actief", "geupload" of "gearchiveerd".
    - `uuid` (TEXT): Unieke sessie identifier.
- **Indexen**: `windrichting`, `begintijd`, `onlineid`, `telpostid`.

### 12.2 Tabel: `waarnemingen`
**Doel**: Bevat de individuele vogelrecords per sessie.
- **Primary Key**: `idLocal` (TEXT) + `tellingid` (TEXT)
- **Foreign Key**: `tellingid` naar `telling_headers.tellingid` (CASCADE).
- **Velden**:
    - `soortid` (TEXT): Matcht met `species.json`.
    - `aantal`, `aantalterug`, `lokaal` (TEXT).
    - `richting`, `richtingterug` (TEXT): De vliegrichting codes.
    - `tijdstip` (TEXT): Exacte tijdstip van waarneming (Epoch).
    - `geslacht`, `leeftijd`, `kleed` (TEXT): Annotaties.
- **Indexen**: `tellingid`, `soortid`, `tijdstip`, `(soortid, tijdstip)`.

### 12.3 Tabel: `weather_archive`
**Doel**: Massa-opslag van uurlijkse weergegevens voor alle telposten en referentiepunten.
- **Primary Key**: `locationId` (TEXT) + `timeEpoch` (INTEGER/Long)
- **Velden**:
    - `temp`, `windSpeed10m`, `windDir10m` (DOUBLE).
    - `pressureMsl`, `cloudCover`, `precip` (DOUBLE).
- **Indexen**: `locationId`, `timeEpoch`.

### 12.4 Tabel: `daily_analysis`
**Doel**: Wetenschappelijke daganalyse (geaggregeerde resultaten en weer).
- **Primary Key**: `dayEpoch` (INTEGER/Long)
- **Velden**:
    - `type` (TEXT): "LIVE" of "RECONSTRUCTED".
    - `weatherJson`, `effortJson`, `resultsJson` (TEXT): Gecomprimeerde JSON data.
    - `remarks` (TEXT): Notities bij de dag.

### 12.5 Tabel: `species_phenology_vault`
**Doel**: Geoptimaliseerde kluis voor trek-curves per regio.
- **Primary Key**: `speciesId` (TEXT) + `clusterId` (TEXT)
- **Velden**:
    - `dailyBphSeries` (TEXT): 366 floats gescheiden door `|`.
    - `peakSpring`, `peakAutumn` (TEXT): Berekende piekmomenten.

### 12.6 Tabel: `ai_logs`
**Doel**: Geschiedenis van getoonde prognoses en gebruikersfeedback.
- **Primary Key**: `id` (INTEGER, auto-increment)
- **Velden**:
    - `requestContext` (TEXT): Weercondities tijdens de prognose.
    - `suggestions` (TEXT): De voorspelde soorten en kansen.
    - `rating` (INTEGER): 0-5 sterren.

### 12.7 Tabel: `species_images`
**Doel**: Lokale cache voor vogelthumbnails (BLOB).
- **Primary Key**: `latinName` (TEXT)
- **Velden**:
    - `thumbnailBlob` (BLOB): Binaire afbeeldingsdata.
    - `lastUpdated` (INTEGER/Long): Tijdstempel van de laatste update.

### 12.8 Tabel: `sync_logs`
**Doel**: Logboek voor server-synchronisatie en uploadpogingen.
- **Primary Key**: `id` (INTEGER, auto-increment)
- **Velden**:
    - `tellingid` (TEXT)
    - `onlineid` (TEXT)
    - `timestamp` (TEXT)
    - `requestPayload` (TEXT)
    - `serverResponse` (TEXT)
    - `success` (TEXT): "1" voor succes, "0" voor falen.
