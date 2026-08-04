# Reconstructie AI & Weer-Grid Componenten

Dit document dient als blauwdruk voor het herstel van de Bio-Intelligence functies, met behoud van de huidige stabiliteit.

## 1. Stabiliteits-garantie (Side-by-Side)
- **Geen wijzigingen aan Core**: We raken `TellingScherm.kt`, `SpeechHandler.kt` en `TellingUploadCore.kt` NIET aan.
- **Additieve Database**: De nieuwe tabel `weather_archive` wordt toegevoegd zonder de bestaande tabellen te wijzigen.

## 2. Het 21-punts Grid (Zeeslag) - Dynamische Locaties
- **Grid Center Logica**: Het grid (A1-C7) wordt berekend rondom de "Actieve Telpost".
- **50km Redundantie**: De app herkent of een telpost binnen 50km van een bestaand grid ligt om credit-verbruik te minimaliseren.

## 3. Instellingen UI (Tabblad Layout)
- **Tab 1: Basis**: Bestaande instellingen.
- **Tab 2: AI & Bio-Intelligence**: Beheer van telposten en AI status.

## 4. Smart Batch Import (Excel .xlsx) - Bevestigde Verbindingslogica
Hoewel de samples in `serverdata-samples` CSV-bestanden waren, is bevestigd dat de Excel-bestanden exact dezelfde kolommen en logica hanteren.

### Koppeling Schema (Excel):
- **Header Sheet**: Kolom 1 (`id`) bevat het unieke sessienummer.
- **Data Sheet**: Kolom 4 (`countid`) bevat de link naar de sessie.
- **Dataid**: Wordt gebruikt voor de-duplicatie van individuele waarnemingen.

### Import Strategie (100% Foutloos):
1. **Excel Parser**: We gebruiken een gespecialiseerde bibliotheek om `.xlsx` bestanden direct uit te lezen (geen CSV conversie nodig).
2. **De-duplicatie**:
   - De `id` uit de header wordt opgeslagen als `onlineid`.
   - De `dataid` uit de waarneming wordt de `onlineid`.
   - Voor elke rij: *"Bestaat deze onlineid al in Room?"* -> Indien ja, sla de rij volledig over.
3. **Datum Conversie**: Gebruik van de bliksemsnelle `Epoch_tijdstip` kolom uit de Excel voor directe Room-opslag.

## 5. Room Database Uitbreiding
- **Entity**: `WeatherArchive.kt`.
- **Primary Key**: `(locationId, timeEpoch)`.
- **Integriteit**: Unique constraints om dubbel weer te voorkomen.

---

### Volgende stappen:
1. **Stap 1**: Toevoegen van de `WeatherArchive` entity en DAO methoden.
2. **Stap 2**: Bouwen van de `ExcelImportManager` met de `id <-> countid` logica.

**Ik ben volledig op de hoogte van de verbindingslogica en klaar voor de bouw.**
