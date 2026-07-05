# Plan van Aanpak: CSV Import Framework

## Doel
Het geautomatiseerd importeren van historische telgegevens (CSV) in de lokale database van VoiceTally, waarbij metadata en waarnemingen correct worden gekoppeld.

## Veiligheid (Garantie tegen Dubbele Data)
⚠️ **CRITICAL**: Alle geïmporteerde records krijgen de status `archief` of `geimporteerd`. De upload-functionaliteit van de app wordt zo aangepast dat records met deze status **nooit** naar de server worden gestuurd. Dit voorkomt dubbele data op Trektellen.nl.

## Datamapping Strategie

### 1. Metadata (`Trektellen_headerdata_...csv`)
De volgende velden worden gemapt naar de `TellingHeader` entiteit:
- `id` -> `tellingid` (Unieke sleutel)
- `start` / `stop` -> `begintijd` / `eindtijd`
- `winddirection` -> `windrichting`
- `windspeed_bfr` -> `windkracht`
- `temperature` -> `temperatuur`
- `cloudcover` -> `bewolking`
- `siteid` -> `telpostid`
- **Status** -> Wordt hardcoded gezet op `gearchiveerd` (voorkomt upload).

### 2. Telgegevens (`Trektellen_data_...csv`)
De volgende velden worden gemapt naar de `Waarneming` entiteit:
- `dataid` -> `idLocal` (Unieke sleutel voor de waarneming)
- `countid` -> `tellingid` (De koppeling naar de header)
- `speciesid` -> `soortid`
- `local` -> `aantal` (Let op: in Trektellen CSV is 'local' vaak de som van getelde individuen per record)
- `timestamp` -> `tijdstip`
- `age`, `sex`, `plumage` -> mappen naar respectievelijke velden.

## Framework Architectuur

### Nieuwe Componenten:
1.  **`CsvImportManager`**: De motor die de CSV-bestanden leest, parseert en naar de DAO stuurt.
2.  **`ImportRepository`**: Handelt de database-transacties af (Header eerst, dan Waarnemingen).
3.  **`CsvParserUtils`**: Utility om om te gaan met verschillende scheidingstekens (komma vs puntkomma).

### Wijzigingen in bestaande code:
- **`TellingHeader`**: Eventueel een extra status `imported` toevoegen.
- **`DatabaseBeheerScherm`**: Toevoegen van een "Import" knop en voortgangsindicator.
- **Upload Logica**: Expliciete check toevoegen: `if (header.status == "gearchiveerd") return`.

## Data Integriteit Check
Door de `id` van de header te gebruiken als `tellingid` in de database, garandeert Room via de `PRIMARY KEY` dat we nooit dezelfde sessie twee keer importeren (het wordt overschreven/bijgewerkt).

## Volgende Stappen
1. Implementatie van de `CsvImportManager`.
2. UI-koppeling in het instellingen- of databasebeheerscherm.
3. Testrun met de data in `serverdata-samples`.
