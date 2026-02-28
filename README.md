# VT5 — Gebruikershandleiding

> **VT5** is een snelle, intuïtieve Android-app voor het vastleggen van vogeltrekwaarnemingen via spraakinvoer. De app is ontworpen voor gebruik in het veld door vogelwaarnemers en synchroniseert automatisch met [www.trektellen.nl](https://www.trektellen.nl).

## Releases
[![Release](https://img.shields.io/github/v/release/YvedD/VoiceTally)](https://github.com/YvedD/VoiceTally/releases/latest/download/app-release-v1.0.1.apk)
![Downloads](https://img.shields.io/github/downloads/YvedD/VoiceTally/total)
![Platform](https://img.shields.io/badge/platform-Android-brightgreen)
![Min SDK](https://img.shields.io/badge/minSDK-33-blue)
[![License](https://img.shields.io/badge/license-CC--BY--NC--SA--4.0-blue)](https://github.com/YvedD/VoiceTally/blob/main/LICENSE.md)

- **Changelog / Versiegeschiedenis:** zie [`CHANGELOG.md`](CHANGELOG.md)

---

## Inhoudsopgave

1. [Eerste Installatie](#1-eerste-installatie)
2. [Permissies Toekennen](#2-permissies-toekennen)
3. [SAF-map Kiezen](#3-saf-map-kiezen)
4. [Automatisch Aangemaakte Bestanden](#4-automatisch-aangemaakte-bestanden)
5. [Server Data Downloaden](#5-server-data-downloaden)
6. [Metadata Instellen](#6-metadata-instellen)
7. [Soorten Kiezen & Recente Soorten](#7-soorten-kiezen--recente-soorten)
8. [Waarneming Toevoegen via Tegels](#8-waarneming-toevoegen-via-tegels)
9. [Waarneming Toevoegen via Spraakinvoer](#9-waarneming-toevoegen-via-spraakinvoer)
10. [Waarneming Annoteren](#10-waarneming-annoteren)
11. [Alias Aanmaken (Spraakinvoer Opslaan)](#11-alias-aanmaken-spraakinvoer-opslaan)
12. [Soorten Toevoegen tijdens Telling](#12-soorten-toevoegen-tijdens-telling)
13. [Huidige Stand Scherm](#13-huidige-stand-scherm)
14. [Telling Afronden](#14-telling-afronden)
15. [Auto-Weather Systeem](#15-auto-weather-systeem)
16. [Audit: Real-time migratie-predictie (AI-gestuurd)](#16-audit-real-time-migratie-predictie-ai-gestuurd)

---

## 1. Eerste Installatie

### Stap 1: App Starten
Na het installeren van de APK start u de VT5-app. U komt terecht op het **Hoofdscherm** met zes knoppen:

| Knop | Functie |
|------|---------|
| **(Her)Installatie** | Opent het installatieproces voor eerste configuratie of herconfiguratie |
| **Invoeren telpostgegevens** | Start een nieuwe telling (na installatie) |
| **Toggle alarm** | Schakelt het uurlijkse alarm in/uit |
| **Bewerk tellingen** | Mogelijkheid om bestaande tellingen nog aan te passen en op te slaan |
| **Opkuis submap 'exports'** | Opschonen van de submap exports op de tien laatste bestanden na |
| **Instellingen** | Stel een aantal gebruikersinstellingen in met betrekking tot de interface |

### Stap 2: Installatiewizard Starten
Klik op **"(Her)Installatie"** om naar het `InstallatieScherm` te gaan.

---

## 2. Permissies Toekennen

VT5 vraagt om de volgende permissies:

| Permissie | Waarvoor nodig |
|-----------|----------------|
| **Microfoon** (`RECORD_AUDIO`) | Spraakherkenning voor het invoeren van waarnemingen |
| **Locatie** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) | Auto-weather functie: ophalen van actuele weergegevens |
| **Opslagtoegang** (via SAF) | Bestanden opslaan en laden in `Documents/VT5/` |
| **Alarm** (`SCHEDULE_EXACT_ALARM`) | Uurlijks alarm op de 59e minuut |
| **Trillen** (`VIBRATE`) | Feedback bij alarmmeldingen |

De app vraagt deze permissies automatisch aan wanneer ze nodig zijn.

---

## 3. SAF-map Kiezen

### Wat is SAF?
SAF (Storage Access Framework) is het moderne opslagsysteem van Android. U kiest zelf de map waar VT5 bestanden mag opslaan.

### Stappen:
1. In het `InstallatieScherm`, klik op **"Kies Documents map"**
2. Android toont een bestandskiezer
3. Navigeer naar uw **Documents**-map (of maak deze aan)
4. Klik op **"Gebruiken"** of **"Toestaan"**
5. De status verandert naar: *"SAF OK - Alle mappen aanwezig"*

### Mappen Controleren/Aanmaken
Klik op **"Controleer/Maak mappen"** om te verifiëren dat alle submappen bestaan:
- `Documents/VT5/assets/`
- `Documents/VT5/serverdata/`
- `Documents/VT5/counts/`
- `Documents/VT5/exports/`
- `Documents/VT5/binaries/`

---

## 4. Automatisch Aangemaakte Bestanden

Na het configureren van SAF maakt VT5 de volgende structuur aan:

```
Documents/VT5/
├── assets/                          # Master data & configuratie
│   ├── alias_master.json           # Alle aliassen (soortnaam-synoniemen)
│   ├── alias_master.meta.json      # Metadata over de alias index
│   ├── alias_index.json            # Exportformaat van aliases
│   ├── annotations.json            # Annotatie-opties (leeftijd, geslacht, kleed)
│
├── binaries/                        # Geoptimaliseerde runtime bestanden
│   ├── aliases_optimized.cbor.gz   # Binaire alias-index (snel laden)
│   └── species_master.cbor.gz      # Soortenlijst (binair)
│
├── serverdata/                      # Gedownloade server data
│   ├── species.json                # Alle vogelsoorten
│   ├── site_species.json           # Soorten per telpost
│   ├── sites.json                  # Telposten/locaties
│   ├── codes.json                  # Weer- en overige codes
│   └── checkuser.json              # Gebruikersinfo na login
│
├── counts/                          # Opgeslagen tellingen
│   └── <timestamp>_count_<id>.json # Per telling een JSON-bestand
│
└── exports/                         # Exports & logs
    └── alias_precompute_log_<ts>.txt
```

---

## 5. Server Data Downloaden

### Inloggegevens Instellen
1. Vul uw **trektellen.nl gebruikersnaam** in bij "Login"
2. Vul uw **wachtwoord** in
3. Klik op **"Bewaar"** om de gegevens veilig op te slaan

### Login Testen
Klik op **"Test login"** om te verifiëren dat uw credentials werken. Bij succes ziet u uw gebruikersinfo.
Als je dit doet worden ook de gebruikergegevens lokaal opgeslagen zodat die later kunnen gebruikt worden voor de upload naar de server.

### Server Data Downloaden
1. Klik op **"Download JSONs van server"**
2. De app downloadt:
   - `species.json` — Alle vogelsoorten
   - `site_species.json` — Soorten per telpost
   - `sites.json` — Beschikbare telposten
   - `codes.json` — Weer- en overige codes

3. Automatisch wordt `annotations.json` aangemaakt in `assets/` met standaard annotatie-opties (leeftijd, geslacht, kleed) indien nog niet aanwezig

4. Na het downloaden wordt automatisch de **alias-index** bijgewerkt

### Alias Index Bijwerken
De alias-index wordt automatisch bijgewerkt na het downloaden van server data. U kunt handmatig een rebuild forceren via **"Forceer heropbouw alias index"**.

### Terug naar Hoofdscherm
Klik op **"Klaar"** om terug te keren naar het hoofdscherm.

---

## 6. Metadata Instellen

Het `MetadataScherm` is waar u de telling voorbereid voordat u begint met waarnemen.

### Scherm Openen
Klik op **"Invoeren telpostgegevens"** in het hoofdscherm.

### Velden Invullen

| Veld | Beschrijving |
|------|-------------|
| **Telpost** | Kies een telpost uit de dropdown (gedownload van server) |
| **Datum** | Automatisch ingevuld met vandaag; klik om aan te passen |
| **Starttijd** | Automatisch ingevuld met huidige tijd; klik om aan te passen |
| **Tellers** | Uw naam (automatisch ingevuld vanuit login), vul manueel aan met collega tellers |
| **Windrichting** | 16-punts kompasroos (N, NNO, NO, ONO, etc.) |
| **Windkracht** | Beaufort schaal (0-12) |
| **Bewolking** | Achtsten (0/8 tot 8/8) |
| **Neerslag** | Geen, motregen, regen, etc. |
| **Temperatuur** | Graden Celsius (C°)|
| **Zicht** | Meters |
| **Luchtdruk** | Hectopascal (hPa) |
| **Weer opmerking** | Vrij tekstveld voor extra weerinfo |

### Verder naar Soortselectie
Na het invullen van de metadata, klik op **"Verder"** om naar het soortenselectiescherm te gaan.
### Voorbeeld screenshot
![Metadatascherm van de app](app/src/main/images/metadatascherm.jpg)
---

## 7. Soorten Kiezen & Recente Soorten

Het `SoortSelectieScherm` toont alle beschikbare vogelsoorten voor uw telling.

### Schermindeling

```
┌───────────────────────────────────────┐
│ [Zoekbalk: Typ om te zoeken]          │
├───────────────────────────────────────┤
│ ═══ Recente Soorten (5) [✓] Alles ══  │ ← Header met "Selecteer alle recente"
│ ┌─────────┐ ┌─────────┐               │
│ │ Buizerd │ │ Koolmees│               │ ← Recent gebruikte soorten
│ └─────────┘ └─────────┘               │
│ ───────────────────────────────────── │ ← Scheidingslijn
│ ┌─────────┐ ┌─────────┐               │
│ │ Aalschol│ │ Appelvk │               │ ← Alfabetische lijst
│ └─────────┘ └─────────┘               │
│ ...                                   │
├───────────────────────────────────────┤
│ Totaal: 245 soorten | 12 geselecteerd │
│        [Annuleer]    [OK]             │
└───────────────────────────────────────┘
```

### Recente Soorten (Quick-Pick)
- **Bovenaan** ziet u de soorten die u recent heeft gebruikt
- Klik op **"Alles"** checkbox om alle recente soorten in één keer te selecteren
- Recente soorten worden automatisch bijgehouden (max. 30 items)

### Zoeken
- Typ in de zoekbalk om snel soorten te vinden
- Zoeken werkt op naam én ID
- De zoekfunctie is accent-insensitief ("e" vindt ook "é")

### Soorten Selecteren
- **Tik** op een soort om te selecteren/deselecteren
- Geselecteerde soorten krijgen een vinkje
- De teller onderaan toont hoeveel soorten zijn geselecteerd

### Bevestigen
Klik op **"OK"** om de selectie te bevestigen en naar het telscherm te gaan.

---

## 8. Waarneming Toevoegen via Tegels

In het `TellingScherm` ziet u uw geselecteerde soorten als tegels (tiles).

### Schermindeling

```
┌──────────────────────────────────────┐
│ ═══ Spraakherkenning Resultaten ═══  │
│ ┌──────────────────────────────────┐ │ ← Tussenstands spraakherkenning
│ │ [Partials - blauw kader]         │ │ ← Een lijn aantikken opent een popup scherm om een alias toe te voegen
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │ ← Definitieve resultaten
│ │ [Finals - groen kader]           │ │ ← Een lijn aantikken opent een annotatiescherm voor die waarneming
│ └──────────────────────────────────┘ │
├──────────────────────────────────────┤ ← Actieknoppen [Overzicht van totalen tot nu toe]
│  [Totalen] [+ Soorten] [Afronden]    │ ← [Soort toevoegen][Uploaden naar server]
├──────────────────────────────────────┤
│ ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│ │ Buizerd │ │ Sperwer │ │ Vink    │  │ ← Soort-tegels met aantallen naar beide richtingen
│ │ 3 . 12  │ │  0 . 0  │ │ 12 . 54 │  │ ← Een tegel aantikken opent een dialoog om handmatig aantallen toe te voegen
│ └─────────┘ └─────────┘ └─────────┘  │
│ ...                                  │
└──────────────────────────────────────┘
```

### Een alias toevoegen via de spraakinvoer/partialsscherm
1. **Tik** op een partials-lijn in het blauwe kader
2. Er verschijnt een dialoog: *"Alias toevoegen voor [partial-tekst]"*
3. Kies een soort om deze alias aan te toe te wijzen, tik op [Toevoegen]
4. Als de gebruiker een lijn kiest waarin ook een aantal is herkend, dan word dit aantal automatisch ingevuld bij de telling / tegel

### Een waarneming aanvullen, richting of andere parameters toevoegen via het finalscherm
1. **Tik** op een 'final-logregel' in het groene kader
2. Je komt terecht in het annotatiescherm, waarin verschillende parameters kunnen worden toegevoegd
3. Je kan ook waarnemingen splitsen over hoofdrichting, tegenrichting en lokaal
4. Er zijn ook checkboxen om de waarneming te markeren of in handteller-modus te zetten
5. Alle wijzigingen worden opgeslagen bij het tikken op [OK]

**Opmerking** Als je in het annotatiescherm aantallen ingeeft en tegelijk ook andere opties aantikt, dan gelden deze andere opties voor alle aantallen, dus ook voor 'tegenrichting' en 'lokaal' !!

### Knoppenbalk
**[Totalen]** : Toont een huidige stand van zaken van de **lopende** telling.<br>
**[Toevoegen]** : Extra soorten manueel toevoegen aan de lopende telling.<br>
**[Afronden]** : Sluit een lopende telling af en upload deze naar de server. (Nadien heb je de keuze om een vervolgtelling te maken).

### Uurlijks alarm
Op elke 59ste minuut van het begonnen uur verschijn het "Totalenscherm" met de huidige stand van zaken voor de lopende telling.<br>
Na controle is het aangeraden om de telling alsnog af te ronden en naar de server te sturen.<br>
Na het uploaden kan je kiezen om niet verder te tellen **[Annuleren] of een vervolgtelling te starten [OK].<br>

### Handmatig Tellen (Tik op Tegel)
1. **Tik** op een soort-tegel
2. Er verschijnt een dialoog: *"Voer aantal in voor [Soortnaam]"*
3. Typ het aantal (bijv. `5`)
4. Klik op **"OK"**
5. Het aantal wordt opgeteld bij de huidige telling

### Resultaat
- De tegel toont het nieuwe totaal
- In het **Finals-venster** verschijnt: `Buizerd -> +5`
- De telling wordt automatisch opgeslagen in een backup-bestand

### Voorbeeld screenshot
![Metadatascherm van de app](app/src/main/images/tellingscherm.jpg)
---

## 9. Waarneming Toevoegen via Spraakinvoer

VT5 is geoptimaliseerd voor snelle spraakherkenning van Nederlandse vogelnamen.

### Spraakherkenning Activeren
- **Volumetoets** (omhoog of omlaag) indrukken en loslaten
- Of automatisch via voice-key handler of een BT-HID knop

### Spraakprotocol
Spreek in het formaat: **"Soortnaam Aantal"**

| U zegt | Resultaat |
|--------|-----------|
| "Buizerd vijf" | Buizerd +5 |
| "Koolmees" | Koolmees +1 (impliciet 1) |
| "Wilde eend tien" | Wilde Eend +10 |
| "Vink twee" | Vink +2 |

### Nederlandse Getallen
VT5 herkent Nederlandse telwoorden:
- één, twee, drie, vier, vijf, zes, zeven, acht, negen, tien
- elf, twaalf, dertien, veertien, etc.
- twintig, dertig, veertig, vijftig, etc.

### Partials vs Finals
- **Partials** (blauw kader): Tussenresultaten terwijl u spreekt
- **Finals** (groen kader): Definitief herkende en geregistreerde waarnemingen

### Soort Niet in Tegels
Als u een soort noemt die niet in uw tegels zit:
1. VT5 toont een bevestigingsdialoog
2. *"Soort 'Wielewaal' herkend met aantal 2. Toevoegen?"*
3. Kies **"Ja"** om de soort toe te voegen, of **"Nee"** om te annuleren

### Suggestielijst
Bij onduidelijke herkenning toont VT5 een suggestielijst met kandidaten. Tik op de juiste soort om te selecteren.

### Tips
Sommige soorten zijn door de aard van Android Speech Recognition (hoofdzakelijk ontwikkeld voor een Engelstalig taalgebied) minder gemakkelijke te herkennen.
Soorten zoals 'fuut" worden vaak genegeerd omdat ze teveel lijken op het Engelse woord 'fu#@ck'.
Bij moeilijke soorten is het dan ook aangeraden om met verkleinwoorden te werken of meervouden "futen" - "sijsjes" - "kauwen".
Soorten waarvan de naam begint met een getal is ook niet altijd als dusdanig herkenbaar voor de spraakinvoer (denk aan "drieteenmeeuw" - "drieteenstrandloper") dit komt omdat het algoritme eerst een 'soortnaam' verwacht en pas daarna het aantal exemplaren.

---

## 10. Waarneming Annoteren

U kunt waarnemingen annoteren met extra details zoals leeftijd, geslacht, kleed, locatie en hoogte.

### Annotatiescherm Openen
1. **Tik** op een regel in het **Finals-venster** (groene kader)
2. Het `AnnotatieScherm` opent

### Beschikbare Annotaties

| Categorie | Opties |
|-----------|--------|
| **Leeftijd** | adult, 1e-kj, 2e-kj, 3e-kj, onbekend, etc. |
| **Geslacht** | man, vrouw, onbekend |
| **Kleed** | zomer, winter, overgangskleed, etc. |
| **Locatie** | over telpost, passend, rustend, etc. |
| **Hoogte** | < 10m, 10-50m, 50-100m, > 100m, etc. |
| **Markeren** | Speciale waarneming markeren |
| **Handteller** | Tally-telling modus |

### Aantallen Aanpassen
- **Hoofdrichting** (ZW of NO afhankelijk van seizoen - de periode wordt automatisch bepaald )
- **Tegenrichting**
- **Lokaal** (lokale vogels, niet trekkend)

### Kompas
- Er is een werkend kompas aanwezig, waarmee de gebruikers een afwijkende vliegroute precies kunnen ingeven, op basis van real-time kompas gegevens

### Opmerkingen
Veld voor vrije tekst (bijv. bijzondere kenmerken)

### Opslaan
Klik op **"OK"** om de annotatie op te slaan. De annotatie wordt gekoppeld aan de specifieke waarneming.

---

## 11. Alias Aanmaken (Spraakinvoer Opslaan)

Als VT5 een gesproken tekst niet herkent, kunt u deze als **alias** opslaan voor toekomstig gebruik.

### Wanneer Aliassen Gebruiken?
- Regionale namen ("ekster" vs "Euraziatische Ekster")
- Afkortingen ("bui" voor "Buizerd")
- Fonetische varianten ("koolmees" vs "koolmeest")

### Alias Aanmaken
1. **Tik** op een niet-herkende tekst in het **Partials-venster** (blauw kader)
2. Het `AddAliasDialog` opent
3. Kies de **gesproken tekst** (indien meerdere opties)
4. Selecteer de **doelsoort** via autocomplete
5. Klik op **"Toevoegen"**

### Resultaat
- De alias wordt opgeslagen in `alias_master.json`
- De alias-index (`aliases_optimized.cbor.gz`) wordt bijgewerkt
- Volgende keer wordt deze spraakvariant automatisch herkend

### Voorbeeld
```
Gesproken: "bui"
Gekoppeld aan: "Buizerd"

Volgende keer: "bui vijf" → Buizerd +5
```

---

## 12. Soorten Toevoegen tijdens Telling

U kunt extra soorten toevoegen terwijl een telling actief is.

### Soorten Toevoegen
1. Klik op **"+ Soorten"** in het telscherm
2. Het `SoortSelectieScherm` opent
3. Selecteer extra soorten
4. Klik op **"OK"**
5. De nieuwe soorten verschijnen als tegels in het telscherm

### Automatisch Toevoegen via Spraak
Als u via spraak een soort noemt die niet in de tegels zit, biedt VT5 aan om deze toe te voegen (zie sectie 9).

---

## 13. Huidige Stand Scherm

Het `HuidigeStandScherm` toont een overzicht van alle getelde soorten met hun aantallen.

### Scherm Openen
Klik op **"Totalen"** (of "Huidige stand") in het telscherm.

### Overzicht

```
┌────────────────────────────────────────────┐
│          Huidige Stand                     │
├────────────┬────────┬───────┬──────────────┤
│ Soortnaam  │ Totaal │  ZW   │     NO       │
├────────────┼────────┼───────┼──────────────┤
│ Buizerd    │   15   │  12   │      3       │
│ Sperwer    │    8   │   8   │      0       │
│ Vink       │  250   │ 200   │     50       │
│ ...        │  ...   │ ...   │    ...       │
├────────────┴────────┴───────┴──────────────┤
│ Totaal: 523 | ZW: 420 | NO: 103            │
│                                            │
│              [OK - Terug]                  │
└────────────────────────────────────────────┘
```

### Kolommen
- **Soortnaam**: Naam van de soort
- **Totaal**: Som van hoofdrichting + tegenrichting
- **ZW/NO**: Aantallen per richting (labels afhankelijk van seizoen)

### Seizoensafhankelijke Labels
- **ZW-seizoen** (juli-december): Hoofdkolom = "ZW", Terugkolom = "NO"
- **NO-seizoen** (januari-juni): Hoofdkolom = "NO", Terugkolom = "ZW"

### Terug naar Telling
Klik op **"OK"** om terug te keren naar het telscherm.

---

## 14. Telling Afronden
Op elke 00e minuut van het uur herinnert de app u eraan om een telling af te ronden via een alarmmelding (uitschakelbaar)
Na het voltooien van uw telling uploadt u de gegevens naar trektellen.nl.

### Afronden
1. Klik op **"Afronden"** in het telscherm
2. VT5 vraagt om bevestiging: *"Weet je zeker dat je wilt afronden?"*
3. Klik op **"Ja"** om te bevestigen

### Upload Proces
1. VT5 bouwt een `counts_save` envelope met:
   - Alle metadata (telpost, datum, tijd, weer)
   - Alle waarnemingen met annotaties
2. De envelope wordt geüpload naar trektellen.nl
3. Bij succes:
   - Lokale backup-bestanden worden opgeruimd
   - De telling wordt opgeslagen in `counts/` als archief
   - U keert terug naar het MetadataScherm
4. Na het uploaden kan men kiezen om een 'aansluitende' vervolgtelling te starten, om zo de tellingen verder te zetten.

### Foutafhandeling
- Bij netwerkfouten blijven de gegevens lokaal bewaard
- U kunt later opnieuw proberen via **"Afronden"**

### Archief
Afgeronde tellingen worden opgeslagen als:
```
Documents/VT5/counts/<timestamp>_count_<online_id>.json
```

---

## 15. Auto-Weather Systeem

VT5 kan automatisch actuele weergegevens ophalen via GPS en een weer-API.

### Functie Activeren
1. In het `MetadataScherm`, klik op **"Auto Weer"** (of wolk-icoon)
2. Bij eerste gebruik: sta locatiepermissie toe
3. VT5 haalt de huidige locatie op
4. Weergegevens worden automatisch ingevuld

### Automatisch Ingevulde Velden

| Veld | Bron |
|------|------|
| Windrichting | GPS + weer-API (16-punts kompas) |
| Windkracht | Windsnelheid omgezet naar Beaufort |
| Bewolking | Cloud cover % omgezet naar achtsten |
| Neerslag | Precipitation code |
| Temperatuur | Actuele temperatuur in °C |
| Zicht | Visibility in meters |
| Luchtdruk | Sea-level pressure in hPa |

### Na Auto-Weather
- De **"Auto Weer"** knop wordt blauw gekleurd
- De knop wordt uitgeschakeld (om dubbel ophalen te voorkomen)
- U kunt handmatig waarden aanpassen indien nodig

### Weer API
VT5 gebruikt de Open-Meteo API voor weergegevens (gratis, geen API-key nodig).

---

## 16. Audit: Real-time migratie-predictie (AI-gestuurd)

Deze sectie is een **onderzoek/audit** (geen directe code-implementatie) voor een nieuwe functie op het opstartscherm: een extra knop of mini-grafiek die de **trek-kans voor de komende 5 dagen** toont op basis van lokale weersverwachting en weersituatie noord/zuid van de huidige locatie.

### 16.1 Huidige situatie in VT5 (relevante bouwstenen)

- Er is al een duidelijk **opstartscherm** (`HoofdActiviteit`) waar een extra knop of grafiekblok logisch kan landen.
- Er is al een werkende **weer-integratie** in de app:
  - locatie via GPS/last-known location
  - weerdata via Open-Meteo (`WeatherManager`)
  - mapping naar vogelpraktijkvelden (windrichting, Beaufort, neerslag, zicht, luchtdruk)
- Dit betekent dat een eerste versie zonder zware architectuurwijziging haalbaar is.

### 16.2 Eenvoudig implementeerbaar systeem (aanbevolen start)

**Doel:** elke dag (D+0 t/m D+4) een migratie-score 0–100 berekenen + trendgrafiek tonen.

**Stap A — Weerdata in 3 zones ophalen**
- **Lokale zone:** rond huidige GPS (bv. straal 30–50 km).
- **Zuid-zone** (voorjaar): representatieve punten in noord/centraal Frankrijk.
- **Noord-zone** (najaar): representatieve punten in NL/Noord-Duitsland/Denemarken-zuid.

**Stap B — Seizoen bepalen**
- Jan–Jun: voorjaarstrek (focus op zuidelijke aanvoer).
- Jul–Dec: najaarstrek (focus op noordelijke aanvoer).

**Stap C — Regelgebaseerde score (v1, “AI-ready”)**
- Start met transparante regels:
  - gunstige rugwind in bronzone + gunstige wind lokaal => score omhoog
  - tegenwind / zware neerslag / slecht zicht => score omlaag
  - consistente gunstige situatie over meerdere zones => bonus
- Dit levert onmiddellijk bruikbare voorspellingen op en vormt tegelijk trainingsdata voor latere echte AI.

**Stap D — UI op opstartscherm**
- Optie 1: knop **“Migratieprognose”** naar detailscherm.
- Optie 2: mini-lijngrafiek (5 dagen) direct onderaan op hoofdscherm.
- Toon telkens ook een korte tekst: “laag / matig / goed / topcondities”.

### 16.3 Waarom dit goed aansluit bij jouw voorbeeld

Jouw casus (voorjaar, gunstige Z–O stroming in zuid/centraal Frankrijk + O/ZO aan Belgische kust) kan in bovenstaand model direct als **“synoptische meewind-corridor”** worden gescoord, met verhoogde kans op sterke migratie.

### 16.4 Drie implementatiepaden met voor- en nadelen

#### Pad A — Heuristisch (snelste route)
- **Beschrijving:** volledig regelgebaseerd, zonder ML-model.
- **Voordelen:** snel, uitlegbaar, weinig risico, offline caching eenvoudig.
- **Nadelen:** minder adaptief; vergt handmatig tunen per seizoen/regio.

#### Pad B — Hybride (aanbevolen middellange termijn)
- **Beschrijving:** heuristiek als baseline + lichte AI-correctie (bijv. regressiemodel op historische data).
- **Voordelen:** betere nauwkeurigheid, nog steeds goed uitlegbaar.
- **Nadelen:** nood aan datasetopbouw en periodieke modelvalidatie.

#### Pad C — Volledig AI-gestuurd
- **Beschrijving:** model voorspelt rechtstreeks migratie-intensiteit uit weerfeatures.
- **Voordelen:** potentieel hoogste performantie.
- **Nadelen:** complexiteit, meer MLOps, moeilijker te debuggen/uitleggen in veldgebruik.

### 16.5 Databronnen: “lokale weerstations” pragmatisch benaderen

- Primair: weer-API met hoge-resolutie rasterdata + dichtstbijzijnde gridpunten.
- Optioneel: verrijken met station-georiënteerde bronnen waar beschikbaar.
- Praktische richtlijn: gebruik altijd “nearest representative points” per zone zodat de gebruiker effectief lokale benadering krijgt.

### 16.6 Aanbevolen fasering

1. **Fase 1:** Pad A (heuristisch), 5-daagse score + eenvoudige grafiek op startscherm.  
2. **Fase 2:** logging van voorspelling vs. geobserveerde trekintensiteit.  
3. **Fase 3:** hybride AI-correctielaag (Pad B) op basis van verzamelde data.  

Deze aanpak houdt de implementatie eenvoudig, levert snel waarde in het veld, en laat toe om gecontroleerd door te groeien naar “echte AI”.

---

### 16.7 Grondig modelvoorstel: van data tot interpretatie

Onderstaand voorstel beschrijft hoe het model **operationeel** kan werken zonder de app-architectuur zwaar te veranderen.

#### 16.7.1 Doeloutput van het model (wat de gebruiker ziet)

Voor elke dag D+0 t/m D+4:
- **Migratie-index**: 0–100
- **Klasse**: laag / matig / goed / top
- **Richtingstype**: vooral hoofdrichting / gemengd / weinig gericht
- **Betrouwbaarheid**: laag / middel / hoog
- **Korte reden** (uitlegbaar): bv. “meewind-corridor zuid->lokaal + droog”

Zo blijft het systeem zowel praktisch als uitlegbaar in veldgebruik.

#### 16.7.2 Data-inname (features) in vier blokken

**A) Lokale features (huidige GPS-zone)**
- windrichting, windsnelheid, windstoten
- neerslag, zicht, luchtdruk, temperatuur
- tijdvenster ochtend/middag/avond (trekgedrag is tijdsafhankelijk)

**B) Aanvoerlijn-features (upstream corridors)**
- voorjaar: zuidelijke corridor (meerdere punten noord + centraal Frankrijk)
- najaar: noordelijke corridor (meerdere punten NL/Noord-Duitsland/Denemarken-zuid)
- per corridorpunt dezelfde weatherfeatures als lokaal
- extra: ruimtelijke consistentie (hoe homogeen gunstige stroming is over de lijn)

**C) Synoptische dynamiek (verandering doorheen de tijd)**
- trend 24u/48u: wind draait gunstiger of ongunstiger?
- front-achtige overgangen: abrupte neerslag- en drukwissels
- stabiliteitsscore: blijven condities meerdere uren/dagen gunstig?

**D) Contextfeatures (belangrijke extra beslissingsvariabelen)**
- dag in seizoen (vroeg/midden/laat voorjaar of najaar)
- daglengte / zonsopgang-zonsondergang (proxy voor trekactiviteit)
- maanfase (optioneel; nuttig voor nachtelijke trek)
- persistentie uit observatiehistoriek (bv. laatste 2–3 dagen lokale intensiteit)
- telpostprofiel (kust/inland/landschapstunnel) als latere verfijning

#### 16.7.3 Evaluatiestap: scoringskern (AI-ready)

Gebruik een **twee-lagen aanpak**:

1. **Rule Engine (basis)**
   - hard afkapcriteria (bv. zeer zware regen + slechte zichtbaarheid)
   - positieve regels (corridor-meewind + lokaal gunstige afbuiging)
   - negatieve regels (tegenwind in corridor of lokaal, onstabiele condities)

2. **Model Corrector (fase 2/3)**
   - licht model (regressie/gradient boosting) dat basis-score corrigeert
   - input = alle features + rule-score
   - output = gecorrigeerde index + calibrated confidence

Deze structuur geeft snel resultaat én ondersteunt latere AI-verbetering zonder black-box vanaf dag 1.

#### 16.7.4 Concreet scorekader (voorbeeld, eenvoudig te kalibreren)

Totale score 0–100 als gewogen som:
- **35% lokale vliegcondities**
- **40% aanvoerlijn-condities**
- **15% trend/stabiliteit**
- **10% contextfactoren**

Interpretatie:
- 0–24: laag
- 25–49: matig
- 50–74: goed
- 75–100: top

Belangrijk: gewichten zijn startwaarden; verfijnen op basis van echte waarnemingsdata.

#### 16.7.5 Betrouwbaarheid en foutmarge

Bereken naast de score ook een betrouwbaarheid:
- data-compleetheid (ontbrekende punten?)
- model-overeenstemming (rule en model wijzen dezelfde richting uit?)
- ruimtelijke consistentie (zones niet tegenstrijdig?)

Als betrouwbaarheid laag is, toon expliciet:  
“Voorspelling is voorlopig — met lage zekerheid door inconsistente corridor-data.”

#### 16.7.6 Uitlegbaarheid (essentieel voor vertrouwen)

Sla per dag de topdrivers op:
- + “gunstige ZO/O-stroming op 4 van 6 corridorpunten”
- + “droog + goed zicht lokaal”
- - “tijdelijke tegenwindpiek in namiddag”

De gebruiker ziet dus niet enkel een getal, maar ook *waarom*.

#### 16.7.7 Validatie-aanpak (haalbaar en pragmatisch)

Gebruik bestaande tellingen als referentie en label grove intensiteitsklassen:
- laag / matig / goed / top op basis van aantallen per uur of per sessie
- evalueer per seizoen apart (voorjaar en najaar hebben andere dynamiek)

Kernmetrics:
- classificatie-accuratesse per klasse
- calibration error (komt 80%-confidence overeen met realiteit?)
- false positives op “top”-dagen (operationeel zeer belangrijk)

#### 16.7.8 Gefaseerd pad naar productie

**Sprint 1 (2–3 weken)**
- data-inname lokaal + corridor
- rule-engine score 5 dagen
- eenvoudige visualisatie + korte reden

**Sprint 2 (2–4 weken)**
- logging pipeline en evaluatiedashboard
- eerste kalibratie van gewichten
- betrouwbaarheidsscore in UI

**Sprint 3 (4+ weken)**
- model-corrector trainen op historiek
- A/B vergelijking: rule-only vs hybrid
- drempels finetunen voor topcondities

#### 16.7.9 Belangrijkste risico’s en mitigatie

- **Risico:** overfitting op één regio/telpost  
  **Mitigatie:** per seizoen valideren op meerdere telposttypes.
- **Risico:** te complexe output voor gebruiker  
  **Mitigatie:** vaste 4-klassen output + 1-zins uitleg.
- **Risico:** data-gaten of API-onbeschikbaarheid  
  **Mitigatie:** caching + fallback op laatste betrouwbare run.

#### 16.7.10 Besluit: haalbaar pad

De meest haalbare route voor VT5:
1. start met uitlegbare rule-engine (snel, robuust, direct bruikbaar),
2. bouw simultaan datalogging op,
3. voeg daarna lichte AI-correctie toe voor nauwkeurigheid.

Zo krijg je snel veldwaarde én een gecontroleerde overgang naar een echte AI-tool.

---

## Veelgestelde Vragen (FAQ)

### Q: De app start niet - wat nu?
**A:** Controleer of u alle permissies heeft toegekend. Ga naar Android Instellingen > Apps > VT5 > Permissies.

### Q: Spraakherkenning werkt niet
**A:** Controleer of microfoonpermissie is toegekend. Zorg voor een rustige omgeving voor betere herkenning.

### Q: Mijn soort wordt niet herkend
**A:** Maak een alias aan (zie sectie 11) of zoek de soort handmatig via de zoekfunctie.

### Q: Data is niet gesynced naar trektellen.nl
**A:** Controleer uw internetverbinding. Open de app en klik op "Afronden" om opnieuw te proberen.

### Q: Ik wil een telling annuleren
**A:** Sluit de app zonder op "Afronden" te klikken. De lokale data blijft bewaard. Gebruik de "Afsluiten" knop in het hoofdscherm voor een veilige afsluiting.

---

## Technische Informatie

- **Minimale Android versie**: Android 13 (API 33)
- **Taalondersteuning**: Nederlands (primair)
- **Offline functionaliteit**: Kernfuncties werken zonder internet
- **Data opslag**: Android SAF (Documents/VT5/)
- **Backend**: www.trektellen.nl

---

## Contact & Support

Voor vragen of problemen, neem contact op met de app-ontwikkelaar.

---

*Versie: 1.0.1 | Laatste update: 2025-12-18*
