# API-Documentatie — VoiceTally JSON upload, UI → Room → JSON mappings

Doel: beschrijf de JSON-upload routes, alle gebruikersinvoervelden (MetadataScherm, TellingScherm, AnnotatieScherm), de Room-tabellen en maak een verbindende grid die UI veld → Room kolom → JSON veld koppelt. Rechts staan drie lege kolommen voor jouw notities.

Checklist
- [x] Endpoint(s) en auth omschrijven
- [x] UI-velden inventariseren (Metadata, Telling, Annotatie)
- [x] Room-entiteiten en kolommen opnemen (types, PK/FK, indices)
- [x] Grid (rijen) die UI widget → form variable → Room kolom → JSON DTO veld linkt
- [x] Bestandsreferenties (absolute path + relevante regels)

Belangrijke endpoints

- POST /api/counts_save?language={lang}&versie={versie}
  - Body: JSON array met precies 1 ServerTellingEnvelope
  - Auth: HTTP Basic (username/password)
  - Code: `app\src\main\java\com\yvesds\vt5\net\TrektellenApi.kt` (postCountsSave, lijnen ~39-81)

- POST /api/data_save/{onlineId}
  - Body: JSON array met precies 1 ServerTellingDataItem (per-record upload)
  - Auth: HTTP Basic
  - Code: `app\src\main\java\com\yvesds\vt5\net\TrektellenApi.kt` (postDataSaveSingle, lijnen ~90-126)

Opmerking: beide DTOs gebruiken alleen strings voor velden — zie `app\src\main\java\com\yvesds\vt5\net\Types.kt`.

Room-schema (samenvatting)

- Entity: `telling_headers` (file: `app\src\main\java\com\yvesds\vt5\core\database\entities\TellingHeader.kt`)
  - Primary key: `tellingid` (String)
  - Fields (String): onlineid, externid, timezoneid, bron, telpostid, begintijd, eindtijd, tellers, weer, windrichting, windkracht, temperatuur, bewolking, bewolkinghoogte, neerslag, duurneerslag, zicht, tellersactief, tellersaanwezig, typetelling, metersnet, geluid, opmerkingen, hydro, hpa, equipment, uuid, uploadtijdstip, nrec, nsoort, status
  - Indices: windrichting, begintijd

- Entity: `waarnemingen` (file: `app\src\main\java\com\yvesds\vt5\core\database\entities\Waarneming.kt`)
  - Primary key: composite `(idLocal, tellingid)` (Strings)
  - Foreign key: tellingid -> telling_headers.tellingid (ON DELETE CASCADE)
  - Fields (String): soortid, aantal, richting, aantalterug, richtingterug, sightingdirection, lokaal, aantal_plus, aantalterug_plus, lokaal_plus, markeren, markerenlokaal, geslacht, leeftijd, kleed, opmerkingen, trektype, teltype, location, height, tijdstip, groupid, uploadtijdstip, totaalaantal, onlineid
  - Indices: tellingid, soortid

- Entity: `sync_logs` (file: `app\src\main\java\com\yvesds\vt5\core\database\entities\SyncLog.kt`)
  - Fields: id (auto), tellingid, onlineid, timestamp, requestPayload, serverResponse, success

Room-tabel veld-instellingen (detail)

Hieronder staan per Room-tabel de exacte veldinstellingen (SQL-type, Kotlin-type, default value, PK/FK, indices, beschrijving en code reference). Alle velden in de twee hoofd-entities zijn Kotlin non-nullable Strings en worden in Room als TEXT opgeslagen.

`telling_headers` (file: `app\src\main\java\com\yvesds\vt5\core\database\entities\TellingHeader.kt`, entity lines ~15-48)

| Veld | SQL type | Kotlin type | Default | Primary Key? | Nullable? | FK? | Index? | Beschrijving | Code reference | Veldnaam in 'headerdata' .CSV| Veldnaam in 'data' .CSV |Veldnaam in tellingEnveloppe | Veldnaam in datarecordenveloppe |
|---|---|---|---|---:|---:|---:|---:|---|---|---|---|---|---|
| tellingid | TEXT | String | (geen default) | Yes | No | - | - | Unieke lokale id van de telling (primary key) | `TellingHeader.kt` (15-18) | | | | |
| onlineid | TEXT | String | "" | No | No | - | - | Online id toegekend door server na START / counts_save | `TellingHeader.kt` (17) | | | | |
| externid | TEXT | String | "" | No | No | - | - | Extern id string (externid) gebruikt in envelope externid | `TellingHeader.kt` (18) | | | | |
| timezoneid | TEXT | String | "Europe/Brussels" | No | No | - | - | Timezone identifier voor telling timestamps | `TellingHeader.kt` (19) | | | | |
| bron | TEXT | String | "4" | No | No | - | - | Broncode (app identifier) | `TellingHeader.kt` (20) | | | | |
| telpostid | TEXT | String | "" | No | No | - | - | Telpost/site id geselecteerd door gebruiker | `TellingHeader.kt` (21) | | | | |
| begintijd | TEXT | String | "" | No | No | - | Index | Begintijd epoch seconds als string (gebruik computeBeginEpochSec) | `TellingHeader.kt` (22) | | | | |
| eindtijd | TEXT | String | "" | No | No | - | - | Eindtijd epoch seconds als string (kan leeg voor live telling) | `TellingHeader.kt` (23) | | | | |
| tellers | TEXT | String | "" | No | No | - | - | Namen van tellers (vrij tekst) | `TellingHeader.kt` (24) | | | | |
| weer | TEXT | String | "" | No | No | - | - | Samenvattende weather tekst (optioneel) | `TellingHeader.kt` (25) | | | | |
| windrichting | TEXT | String | "" | No | No | - | Index | Windrichting code (selectie) | `TellingHeader.kt` (26) | | | | |
| windkracht | TEXT | String | "" | No | No | - | - | Windkracht code/value | `TellingHeader.kt` (27) | | | | |
| temperatuur | TEXT | String | "" | No | No | - | - | Temperatuur (nummer) | `TellingHeader.kt` (28) | | | | |
| bewolking | TEXT | String | "" | No | No | - | - | Bewolking (0..8) | `TellingHeader.kt` (29) | | | | |
| bewolkinghoogte | TEXT | String | "" | No | No | - | - | Hoogte van bewolking (indien gebruikt) | `TellingHeader.kt` (30) | | | | |
| neerslag | TEXT | String | "" | No | No | - | - | Neerslagcode | `TellingHeader.kt` (31) | | | | |
| duurneerslag | TEXT | String | "" | No | No | - | - | Duur van neerslag (optioneel) | `TellingHeader.kt` (32) | | | | |
| zicht | TEXT | String | "" | No | No | - | - | Zicht in meters | `TellingHeader.kt` (33) | | | | |
| tellersactief | TEXT | String | "" | No | No | - | - | (intern) of tellers actief zijn | `TellingHeader.kt` (34) | | | | |
| tellersaanwezig | TEXT | String | "" | No | No | - | - | Aantal aanwezige tellers / ja/nee | `TellingHeader.kt` (35) | | | | |
| typetelling | TEXT | String | "" | No | No | - | - | Type telling code | `TellingHeader.kt` (36) | | | | |
| metersnet | TEXT | String | "" | No | No | - | - | Meters net (optioneel) | `TellingHeader.kt` (37) | | | | |
| geluid | TEXT | String | "" | No | No | - | - | Geluid omschrijving/indicator | `TellingHeader.kt` (38) | | | | |
| opmerkingen | TEXT | String | "" | No | No | - | - | Vrij tekst opmerkingen | `TellingHeader.kt` (39) | | | | |
| hydro | TEXT | String | "" | No | No | - | - | Hydrologie/extra veld (HYDRO) | `TellingHeader.kt` (40) | | | | |
| hpa | TEXT | String | "" | No | No | - | - | Luchtdruk (hPa) | `TellingHeader.kt` (41) | | | | |
| equipment | TEXT | String | "" | No | No | - | - | Gebruikte apparatuur | `TellingHeader.kt` (42) | | | | |
| uuid | TEXT | String | "" | No | No | - | - | UUID van device / sessie (optioneel) | `TellingHeader.kt` (43) | | | | |
| uploadtijdstip | TEXT | String | "" | No | No | - | - | Timestamp van upload (set tijdens prepare/upload) | `TellingHeader.kt` (44) | | | | |
| nrec | TEXT | String | "0" | No | No | - | - | Aantal records (nrec) als string | `TellingHeader.kt` (45) | | | | |
| nsoort | TEXT | String | "0" | No | No | - | - | Aantal soorten (nsoort) | `TellingHeader.kt` (46) | | | | |
| status | TEXT | String | "actief" | No | No | - | - | Status waarde: `actief`, `geupload`, `gerarchived` | `TellingHeader.kt` (47) | | | | |

`waarnemingen` (file: `app\src\main\java\com\yvesds\vt5\core\database\entities\Waarneming.kt`, entity lines ~27-54)

| Veld              | SQL type | Kotlin type | Default |               Primary Key? | Nullable? |                                FK? | Index? | Beschrijving                                                                                     | Code reference | Veldnaam in 'data' .CSV | Veldnaam in 'header' .CSV | Veldnaam in tellingEnveloppe | Veldnaam in datarecordenveloppe |
|-------------------|---|---|---|---------------------------:|---:|-----------------------------------:|-------:|--------------------------------------------------------------------------------------------------|---|---|---|---|---|
| idLocal           | TEXT | String | (geen default) | Yes (part of composite PK) | No |                                  - | - | Lokale record id (uniek per record)                                                              | `Waarneming.kt` (28) | | | | |
| onlineid          | TEXT | String | (geen default) |                          - | No |                                  - | - | Dit is een getal dat bij elke bewerking/opzoeking belangrijk is om gegevens aan elkaar te linken | | | | | |
| tellingid         | TEXT | String | (geen default) | Yes (part of composite PK) | No | Yes -> `telling_headers.tellingid` |  Index | FK naar telling_headers, sessie id                                                               | `Waarneming.kt` (29) | | | | |
| soortid           | TEXT | String | "" |                         No | No |                                  - |  Index | Soort id van de waarneming                                                                       | `Waarneming.kt` (30) | | | | |
| aantal            | TEXT | String | "0" |                         No | No |                                  - |      - | Aantal in hoofdrichting                                                                          | `Waarneming.kt` (31) | | | | |
| richting          | TEXT | String | "" |                         No | No |                                  - |      - | Richting code voor hoofdrichting                                                                 | `Waarneming.kt` (32) | | | | |
| aantalterug       | TEXT | String | "0" |                         No | No |                                  - |      - | Aantal tegenrichting                                                                             | `Waarneming.kt` (33) | | | | |
| richtingterug     | TEXT | String | "" |                         No | No |                                  - |      - | Richting code voor tegenrichting                                                                 | `Waarneming.kt` (34) | | | | |
| sightingdirection | TEXT | String | "" |                         No | No |                                  - |      - | Compass / zichtrichting tekst                                                                    | `Waarneming.kt` (35) | | | | |
| lokaal            | TEXT | String | "0" |                         No | No |                                  - |      - | Lokaal geteld aantal (niet in trek)                                                              | `Waarneming.kt` (36) | | | | |
| aantal_plus       | TEXT | String | "0" |                         No | No |                                  - |      - | Extra plus-aantal (hoofdrichting)                                                                | `Waarneming.kt` (37) | | | | |
| aantalterug_plus  | TEXT | String | "0" |                         No | No |                                  - |      - | Extra plus-aantal (tegenrichting)                                                                | `Waarneming.kt` (38) | | | | |
| lokaal_plus       | TEXT | String | "0" |                         No | No |                                  - |      - | Extra lokaal plus                                                                                | `Waarneming.kt` (39) | | | | |
| markeren          | TEXT | String | "0" |                         No | No |                                  - |      - | Markeer-flag (algemeen)                                                                          | `Waarneming.kt` (40) | | | | |
| markerenlokaal    | TEXT | String | "0" |                         No | No |                                  - |      - | Markeer lokaal flag                                                                              | `Waarneming.kt` (41) | | | | |
| geslacht          | TEXT | String | "" |                         No | No |                                  - |      - | Annotatie: geslacht                                                                              | `Waarneming.kt` (42) | | | | |
| leeftijd          | TEXT | String | "" |                         No | No |                                  - |      - | Annotatie: leeftijd                                                                              | `Waarneming.kt` (43) | | | | |
| kleed             | TEXT | String | "" |                         No | No |                                  - |      - | Annotatie: kleed/plumage                                                                         | `Waarneming.kt` (44) | | | | |
| opmerkingen       | TEXT | String | "" |                         No | No |                                  - |      - | Vrij tekst opmerkingen per record                                                                | `Waarneming.kt` (45) | | | | |
| trektype          | TEXT | String | "" |                         No | No |                                  - |      - | Type trek (annotatie)                                                                            | `Waarneming.kt` (46) | | | | |
| teltype           | TEXT | String | "" |                         No | No |                                  - |      - | Teltype annotatie                                                                                | `Waarneming.kt` (47) | | | | |
| location          | TEXT | String | "" |                         No | No |                                  - |      - | Locatie annotatie (land/zee etc.)                                                                | `Waarneming.kt` (48) | | | | |
| height            | TEXT | String | "" |                         No | No |                                  - |      - | Hoogte annotatie                                                                                 | `Waarneming.kt` (49) | | | | |
| tijdstip          | TEXT | String | "" |                         No | No |                                  - |      - | Epoch seconds als string (tijd van waarneming)                                                   | `Waarneming.kt` (50) | | | | |
| groupid           | TEXT | String | "" |                         No | No |                                  - |      - | Groeps-id (meestal equals idLocal) om samenhang te bewaren                                       | `Waarneming.kt` (51) | | | | |
| uploadtijdstip    | TEXT | String | "" |                         No | No |                                  - |      - | Upload timestamp (formatted)                                                                     | `Waarneming.kt` (52) | | | | |
| totaalaantal      | TEXT | String | "0" |                         No | No |                                  - |      - | Som van aantal + aantalterug + lokaal                                                            | `Waarneming.kt` (53) | | | | |

`sync_logs` (file: `app\src\main\java\com\yvesds\vt5\core\database\entities\SyncLog.kt`, lines ~9-18)

| Veld | SQL type | Kotlin type | Default | Primary Key? | Nullable? | FK? | Index? | Beschrijving | Code reference |
|---|---|---|---|---:|---:|---:|---:|---|---|
| id | INTEGER (auto) | Int | 0 (autoGenerate true) | Yes (auto-generated) | No | - | - | Auto-increment primary key voor sync log rij | `SyncLog.kt` (11) |
| tellingid | TEXT | String | (geen default) | No | No | - | - | Telling id waar deze sync bij hoort | `SyncLog.kt` (12) |
| onlineid | TEXT | String | "" | No | No | - | - | Online id geretourneerd door server (indien aanwezig) | `SyncLog.kt` (13) |
| timestamp | TEXT | String | "" | No | No | - | - | Timestamp van de sync actie | `SyncLog.kt` (14) |
| requestPayload | TEXT | String | "" | No | No | - | - | JSON payload die naar server gestuurd is | `SyncLog.kt` (15) |
| serverResponse | TEXT | String | "" | No | No | - | - | Server response body (tekst) | `SyncLog.kt` (16) |
| success | TEXT | String | "0" | No | No | - | - | "1" voor succes, "0" voor mislukking | `SyncLog.kt` (17) |

Netwerk DTOs (server JSON)
- `ServerTellingEnvelope` (see `app\src\main\java\com\yvesds\vt5\net\Types.kt`, lijnen 8-43)
  - Velden (String): externid, timezoneid, bron, _id (idLocal), tellingid, telpostid, begintijd, eindtijd, tellers, weer, windrichting, windkracht, temperatuur, bewolking, bewolkinghoogte, neerslag, duurneerslag, zicht, tellersactief, tellersaanwezig, typetelling, metersnet, geluid, opmerkingen, onlineid, HYDRO, hpa, equipment, uuid, uploadtijdstip, nrec, nsoort, data (array ServerTellingDataItem)

- `ServerTellingDataItem` (zie `Types.kt`, lijnen 45-73)
  - Velden (String): _id (idLocal), tellingid, soortid, aantal, richting, aantalterug, richtingterug, sightingdirection, lokaal, aantal_plus, aantalterug_plus, lokaal_plus, markeren, markerenlokaal, geslacht, leeftijd, kleed, opmerkingen, trektype, teltype, location, height, tijdstip, groupid, uploadtijdstip, totaalaantal

Mapping grid (UI → Form/VM → Room → JSON)

Kolommen:
- Screen: welke UI
- Widget ID / Label: widget id(s)
- UI input type: tekst / dropdown / checkbox / number / toggle
- Form / VM property: variabele of helper-functie die waarde leest
- Room table: telling_headers / waarnemingen
- Room column: exacte kolomnaam
- JSON DTO field: ServerTellingEnvelope / ServerTellingDataItem veldnaam
- Code reference: file + relevante regels
- Voorbeeld waarde
- Note1 | Note2 | Note3 (leeg voor jouw aantekeningen)

-- Metadata (StartTelling) --

| Screen | Widget ID / Label | UI input type | Form / VM property | Room table | Room column | JSON DTO field | Code reference | Example | Note1 | Note2 | Note3 |
|---|---|---|---|---|---|---|---|---:|---|---|---|
| MetadataScherm | `etDatum` + `etTijd` | date / time text | `MetadataFormManager.computeBeginEpochSec()` | `telling_headers` | `begintijd` | `begintijd` | `app\src\main\java\com\yvesds\vt5\features\metadata\helpers\MetadataFormManager.kt` (226-236) and `TellingHeader.kt` (22-23) and `Types.kt` (16-17) | `1657084800` (epoch sec string) | | | |
| MetadataScherm | `acTelpost` (Telpost dropdown) | dropdown (site id) | `MetadataFormManager.gekozenTelpostId` | `telling_headers` | `telpostid` | `telpostid` | `MetadataFormManager.kt` (135-147); `TellingHeader.kt` (21); `Types.kt` (15) | `site_4310` | | | |
| MetadataScherm | `etTellers` | text | `MetadataFormManager.getTellers()` | `telling_headers` | `tellers` | `tellers` | `MetadataFormManager.kt` (318-320); `TellingHeader.kt` (24); `Types.kt` (18) | `J. Jansen` | | | |
| MetadataScherm | `etOpmerkingen` | text (multiline) | `MetadataFormManager.getOpmerkingen()` | `telling_headers` | `opmerkingen` | `opmerkingen` | `MetadataFormManager.kt` (323-328); `TellingHeader.kt` (39); `Types.kt` (33) | `stille ochtend` | | | |
| MetadataScherm | `acWindrichting` | dropdown (code) | `MetadataFormManager.gekozenWindrichtingCode` | `telling_headers` | `windrichting` | `windrichting` | `MetadataFormManager.kt` (153-165); `TellingHeader.kt` (26); `Types.kt` (20) | `NW` (code) | | | |
| MetadataScherm | `acWindkracht` | dropdown (0..12 / bf) | `MetadataFormManager.gekozenWindkracht` | `telling_headers` | `windkracht` | `windkracht` | `MetadataFormManager.kt` (177-185); `TellingHeader.kt` (27); `Types.kt` (21) | `3` | | | |
| MetadataScherm | `acBewolking` | dropdown (0/8..8/8) | `MetadataFormManager.gekozenBewolking` | `telling_headers` | `bewolking` / `bewolkinghoogte` | `bewolking` / `bewolkinghoogte` | `MetadataFormManager.kt` (167-175); `TellingHeader.kt` (29-30); `Types.kt` (23-24) | `4` / `1000` | | | |
| MetadataScherm | `acNeerslag` | dropdown (codes) | `MetadataFormManager.gekozenNeerslagCode` | `telling_headers` | `neerslag` / `duurneerslag` | `neerslag` / `duurneerslag` | `MetadataFormManager.kt` (187-197); `TellingHeader.kt` (31-32); `Types.kt` (25-26) | `regen` / `30` | | | |
| MetadataScherm | `etTemperatuur` | number | `binding.etTemperatuur.text` (direct) | `telling_headers` | `temperatuur` | `temperatuur` | `scherm_metadata.xml` (226-244); `TellingHeader.kt` (28); `Types.kt` (22) | `18` | | | |
| MetadataScherm | `etZicht` | number (meters) | `binding.etZicht.text` | `telling_headers` | `zicht` | `zicht` | `scherm_metadata.xml` (269-288); `TellingHeader.kt` (33); `Types.kt` (27) | `5000` | | | |
| MetadataScherm | `etLuchtdruk` | decimal number | `binding.etLuchtdruk.text` | `telling_headers` | `hpa` | `hpa` | `scherm_metadata.xml` (344-362); `TellingHeader.kt` (41); `Types.kt` (36) | `1013.2` | | | |
| MetadataScherm | `acTypeTelling` | dropdown (typetelling) | `MetadataFormManager.gekozenTypeTellingCode` | `telling_headers` | `typetelling` | `typetelling` | `MetadataFormManager.kt` (200-218); `TellingHeader.kt` (36); `Types.kt` (30) | `teller_event` | | | |

-- Telling (runtime records / waarnemingen) --

| Screen | Widget ID / Label | UI input type | Form / VM property | Room table | Room column | JSON DTO field | Code reference | Example | Note1 | Note2 | Note3 |
|---|---|---|---|---|---|---|---|---:|---|---|---|
| TellingScherm / Dialog | Tile taps / number dialog (`dialog_number_input_dual.xml` / `TellingDialogHelper`) | number input (main + return) | `TellingSpeciesManager.buildObservationRecord(...)` returns `ServerTellingDataItem` | `waarnemingen` | `soortid` | `soortid` | `TellingSpeciesManager.kt` (238-303); `Waarneming.kt` (30-33); `Types.kt` (49-52) | `NL:12345` (soort id) | | | |
| TellingScherm / Dialog | `et_main_count` (`dialog_number_input_dual.xml`) / `et_aantal` (Annotatie) | number | `buildObservationRecord.amountMain` -> `ServerTellingDataItem.aantal` | `waarnemingen` | `aantal` | `aantal` | `dialog_number_input_dual.xml` (ids: et_main_count); `TellingSpeciesManager.kt` (279-283); `Waarneming.kt` (31); `Types.kt` (50) | `7` | | | |
| TellingScherm / Dialog | `et_return_count` / `et_aantalterug` | number | `amountReturn` -> `aantalterug` | `waarnemingen` | `aantalterug` | `aantalterug` | `TellingSpeciesManager.kt` (281-283); `Waarneming.kt` (33); `Types.kt` (52) | `0` | | | |
| TellingScherm / Annotatie | compass button (`btn_compass`) & `tv_selected_sighting_direction` | compass selection / text | `sightingdirection` -> assigned in `TellingAnnotationHandler` | `waarnemingen` | `sightingdirection` | `sightingdirection` | `activity_annotatie.xml` (670-688); `TellingAnnotationHandler.kt` (260-263, 310-315); `Waarneming.kt` (35); `Types.kt` (54) | `NE` | | | |
| AnnotatieScherm | `btn_leeftijd_*` (toggle buttons) | toggle selection | `map['leeftijd']` applied in `TellingAnnotationHandler.applyAnnotationsToPendingRecord` | `waarnemingen` | `leeftijd` | `leeftijd` | `activity_annotatie.xml` (105-200); `TellingAnnotationHandler.kt` (243-249, 296-304); `Waarneming.kt` (43); `Types.kt` (62) | `adult` | | | |
| AnnotatieScherm | `btn_geslacht_*` | toggle selection | `map['geslacht']` -> applied | `waarnemingen` | `geslacht` | `geslacht` | `activity_annotatie.xml` (229-272); `TellingAnnotationHandler.kt` (244-246, 296-304); `Waarneming.kt` (42); `Types.kt` (61) | `male` | | | |
| AnnotatieScherm | `btn_kleed_*` | toggle selection | `map['kleed']` -> applied | `waarnemingen` | `kleed` | `kleed` | `activity_annotatie.xml` (300-387); `TellingAnnotationHandler.kt` (244-246, 296-304); `Waarneming.kt` (44); `Types.kt` (63) | `adult_winter` | | | |
| AnnotatieScherm | `btn_location_*` | toggle selection | `map['location']` -> applied | `waarnemingen` | `location` | `location` | `activity_annotatie.xml` (408-501); `TellingAnnotationHandler.kt` (247-249, 296-304); `Waarneming.kt` (48); `Types.kt` (67) | `over_land` | | | |
| AnnotatieScherm | `btn_height_*` | toggle selection | `map['height']` -> applied | `waarnemingen` | `height` | `height` | `activity_annotatie.xml` (522-616); `TellingAnnotationHandler.kt` (247-249, 296-304); `Waarneming.kt` (49); `Types.kt` (68) | `high` | | | |
| AnnotatieScherm | `cb_markeren` / `cb_markeren_lokaal` | checkbox | `map['markeren']`, `map['markerenlokaal']` applied | `waarnemingen` | `markeren`, `markerenlokaal` | `markeren`, `markerenlokaal` | `activity_annotatie.xml` (630-655); `TellingAnnotationHandler.kt` (251-254); `Waarneming.kt` (40-41); `Types.kt` (59-60) | `1` / `0` | | | |
| AnnotatieScherm | `et_aantal` / `et_aantalterug` / `et_aantal_lokaal` | number inputs | applied as `aantal`, `aantalterug`, `lokaal` -> `totaalaantal` computed | `waarnemingen` | `aantal`, `aantalterug`, `lokaal`, `totaalaantal` | `aantal`, `aantalterug`, `lokaal`, `totaalaantal` | `activity_annotatie.xml` (710-793); `TellingAnnotationHandler.kt` (251-289, 284-289); `Waarneming.kt` (31-37, 53); `Types.kt` (50,52,55,72) | `3`, `0`, `0`, `3` | | | |
| AnnotatieScherm | `et_opmerkingen` | text | `map['opmerkingen']` -> `opmerkingen` | `waarnemingen` | `opmerkingen` | `opmerkingen` | `activity_annotatie.xml` (812-824); `TellingAnnotationHandler.kt` (256-258, 310-315); `Waarneming.kt` (45); `Types.kt` (64) | `vogel dichtbij` | | | |
| TellingSpeciesManager | internal id allocation | generated idLocal via `DataUploader.getAndIncrementRecordId()` | `ServerTellingDataItem.idLocal` | `waarnemingen` | `idLocal` | `_id` (ServerTellingDataItem._id) | `TellingSpeciesManager.kt` (275-279); `Waarneming.kt` (28); `Types.kt` (47) | `rec_000123` | | | |
| TellingSpeciesManager | `tijdstip` | generated epoch now | `ServerTellingDataItem.tijdstip` | `waarnemingen` | `tijdstip` | `tijdstip` | `TellingSpeciesManager.kt` (256-259, 299-303); `Waarneming.kt` (50); `Types.kt` (69) | `1657084823` | | | |
| TellingAnnotationHandler | `uploadtijdstip` | timestamp string formatted `yyyy-MM-dd HH:mm:ss` | `ServerTellingDataItem.uploadtijdstip` | `waarnemingen` | `uploadtijdstip` | `uploadtijdstip` | `TellingAnnotationHandler.kt` (292-295, 293-294); `Waarneming.kt` (52); `Types.kt` (71) | `2026-07-06 09:12:05` | | | |

Additional notes & mappings

- Room → Server mapping functions: `app\src\main\java\com\yvesds\vt5\core\database\RoomMappers.kt` contains `TellingHeader.toServerEnvelope(records)` and `Waarneming.toServerItem()` that map Room entity fields to `ServerTellingEnvelope` and `ServerTellingDataItem` fields respectively (see file lines ~1-79).

- Upload lifecycle (where mappings are used):
  - Start telling: `StartTellingApi.buildEnvelopeFromUi(...)` builds initial `ServerTellingEnvelope` from Metadata UI and is uploaded via `TellingUploadCore.uploadPrepared` (Start flow). (`app\src\main\java\com\yvesds\vt5\net\StartTellingApi.kt`)
  - During telling: `TellingSpeciesManager.collectFinalAsRecord` creates `ServerTellingDataItem` records, `TellingScherm` keeps them in `pendingRecords` and delegates `HybridTellingRepository.saveWaarnemingToRoom(...)` to persist a shadow copy in Room.
  - Finalize (Afronden): `TellingAfrondHandler.handleAfronden` reads `telling_headers` + `waarnemingen` from Room, runs `RoomMappers.toServerEnvelope()` and posts final `counts_save` via `TellingUploadCore`.

Referenties (belangrijkste bestanden en locaties)
- `app\src\main\java\com\yvesds\vt5\net\TrektellenApi.kt` — API client, endpoints (postCountsSave, postDataSaveSingle)
- `app\src\main\java\com\yvesds\vt5\net\Types.kt` — Server DTOs (ServerTellingEnvelope, ServerTellingDataItem)
- `app\src\main\java\com\yvesds\vt5\features\metadata\helpers\MetadataFormManager.kt` — leest metadata UI velden
- `app\src\main\java\com\yvesds\vt5\features\telling\TellingSpeciesManager.kt` — bouwt waarneming records
- `app\src\main\java\com\yvesds\vt5\features\telling\TellingAnnotationHandler.kt` — past annotaties toe op records
- `app\src\main\java\com\yvesds\vt5\core\database\entities\TellingHeader.kt` — Room header entity
- `app\src\main\java\com\yvesds\vt5\core\database\entities\Waarneming.kt` — Room waarneming entity
- `app\src\main\java\com\yvesds\vt5\core\database\RoomMappers.kt` — Room → Server mapping
- `app\src\main\res\layout\scherm_metadata.xml` — widget ids en layouts (metadata)
- `app\src\main\res\layout\activity_annotatie.xml` — widget ids and annotation controls
- `app\src\main\res\layout\dialog_number_input_dual.xml` — manual number dialog controls

Als je wilt, kan ik:
- extra rijen toevoegen met alle overige (minder gebruikte) velden uit `TellingHeader` en `Waarneming` (nu staan de belangrijkste en direct gekoppelde velden in de grid).
- hetzelfde document als CSV export genereren zodat je het in een spreadsheet kunt bewerken.

Laat me weten of ik nog velden moet detailleren of extra code:regel-referenties moet toevoegen.

-- Server Envelope (ServerTellingEnvelope) — velden, bron & mapping --

De uiteindelijke envelope die naar `POST /api/counts_save` gestuurd wordt is een `ServerTellingEnvelope` (zie `app\\src\\main\\java\\com\\yvesds\\vt5\\net\\Types.kt`). Hieronder staat per veld waar de waarde vandaan komt (UI scherm of Room), in welke Room-kolom deze wordt opgeslagen (indien van toepassing) en de relevante code-referenties.

| Envelope veld | JSON naam | Bron (UI / Room / Code) | UI widget of Room kolom | Waar gevuld / relevant code | Voorbeeld | Note1 | Note2 | Note3 |
|---|---|---|---|---|---:|---|---|---|
| Extern id | `externid` | Hardcoded / StartTellingApi | - | `StartTellingApi.buildEnvelopeFromUi` (36-38) sets `externId = "VoiceTally5 v.1.f"` | `VoiceTally5 v.1.f` | | | |
| Timezone id | `timezoneid` | Hardcoded / StartTellingApi or Room header | `telling_headers.timezoneid` | `StartTellingApi.buildEnvelopeFromUi` (37) / `TellingHeader.timezoneid` | `Europe/Brussels` | | | |
| Bron | `bron` | Hardcoded / StartTellingApi / Room header | `telling_headers.bron` | `StartTellingApi.buildEnvelopeFromUi` (38) / `TellingHeader.bron` | `4` | | | |
| _id (local) | `_id` | Initially empty; server may ignore | `telling_headers.idLocal` not stored | `StartTellingApi.buildEnvelopeFromUi` sets `idLocal = ""` (51); `RoomMappers.toServerEnvelope` keeps `idLocal = ""` (16) | `` (empty) | server may assign | | |
| Telling id | `tellingid` | Generated at start (app) / Room | `telling_headers.tellingid` | `StartTellingApi.buildEnvelopeFromUi` (52); `TellingHeader.tellingid` entity | `vt5_20260706_0001` | | | |
| Telpost id | `telpostid` | Metadata screen (`acTelpost`) / Room | `telling_headers.telpostid` | `MetadataFormManager.gekozenTelpostId` (135-147) -> `StartTellingApi.buildEnvelopeFromUi` param; stored in `TellingHeader.telpostid` | `site_4310` | | | |
| Begintijd | `begintijd` | Metadata screen (`etDatum`+`etTijd`) / Room | `telling_headers.begintijd` | `MetadataFormManager.computeBeginEpochSec()` (226-236) -> passed to `StartTellingApi.buildEnvelopeFromUi` (54) | `1657084800` | epoch seconds as string | | |
| Eindtijd | `eindtijd` | Metadata or live mode / Room | `telling_headers.eindtijd` | `StartTellingApi.buildEnvelopeFromUi` (55) sets `eindtijd` or empty if live; `TellingAfrondHandler` fills on finalize | `` (empty during live) | | | |
| Tellers | `tellers` | Metadata screen (`etTellers`) / Room | `telling_headers.tellers` | `MetadataFormManager.getTellers()` (318-320) -> StartTellingApi param; `TellingHeader.tellers` | `J. Jansen` | | | |
| Weer | `weer` | Metadata screen (`etWeerOpmerking`) / Room | `telling_headers.weer` | `MetadataFormManager.getOpmerkingen()` (323-328) -> StartTellingApi param (71) | `bewolkt, lichte regen` | | | |
| Windrichting | `windrichting` | Metadata screen (`acWindrichting`) / Room | `telling_headers.windrichting` | `MetadataFormManager.gekozenWindrichtingCode` (156-164) -> StartTellingApi param; stored in header | `NW` | code value | | |
| Windkracht | `windkracht` | Metadata screen (`acWindkracht`) / Room | `telling_headers.windkracht` | `MetadataFormManager.gekozenWindkracht` (177-185) -> StartTellingApi param; stored in header | `3` | Beaufort code as string | | |
| Temperatuur | `temperatuur` | Metadata screen (`etTemperatuur`) / Room | `telling_headers.temperatuur` | `binding.etTemperatuur.text` -> StartTellingApi param (40-43) stored in header | `18` | degrees C as string | | |
| Bewolking | `bewolking` | Metadata screen (`acBewolking`) / Room | `telling_headers.bewolking` | `MetadataFormManager.gekozenBewolking` (167-175) -> StartTellingApi | `4` | 0..8 | additional `bewolkinghoogte` may be empty | |
| Bewolkinghoogte | `bewolkinghoogte` | Not set by UI (blank) / Room | `telling_headers.bewolkinghoogte` | `StartTellingApi` sets `bewolkinghoogte = ""` (62) | `` | | | |
| Neerslag | `neerslag` | Metadata screen (`acNeerslag`) / Room | `telling_headers.neerslag` | `MetadataFormManager.gekozenNeerslagCode` (188-197) -> StartTellingApi | `` or code | | | |
| Duur neerslag | `duurneerslag` | Not set by UI (blank) / Room | `telling_headers.duurneerslag` | `StartTellingApi` sets `duurneerslag = ""` (64) | `` | | | |
| Zicht | `zicht` | Metadata screen (`etZicht`) / Room | `telling_headers.zicht` | `binding.etZicht.text` -> StartTellingApi param (65) | `5000` | meters as string | | |
| Tellers actief | `tellersactief` | Not set by Metadata UI (blank) / Room | `telling_headers.tellersactief` | `StartTellingApi` sets `tellersactief = ""` (66) | `` | | | |
| Tellers aanwezig | `tellersaanwezig` | Not set by Metadata UI (blank) / Room | `telling_headers.tellersaanwezig` | `StartTellingApi` sets `tellersaanwezig = ""` (67) | `` | | | |
| Type telling | `typetelling` | Metadata (`acTypeTelling`) / Room | `telling_headers.typetelling` | `MetadataFormManager.gekozenTypeTellingCode` (200-218) -> StartTellingApi param (45,68) | `all` | default `all` if null | | |
| Meters net | `metersnet` | Not set by UI (blank) / Room | `telling_headers.metersnet` | `StartTellingApi` sets `metersnet = ""` (69) | `` | | | |
| Geluid | `geluid` | Not set by UI (blank) / Room | `telling_headers.geluid` | `StartTellingApi` sets `geluid = ""` (70) | `` | | | |
| Opmerkingen | `opmerkingen` | Metadata (`etOpmerkingen`) / Room | `telling_headers.opmerkingen` | `MetadataFormManager.getOpmerkingen()` (323-328) -> StartTellingApi param (71) | `stille ochtend` | | | |
| Online id | `onlineid` | Initially blank; set from server response or stored prefs / Room | `telling_headers.onlineid` | `StartTellingApi` sets `onlineid = ""` (72); `TellingUploadCore` may persist returned onlineId (see lines 219-246, 244-249) and `RoomMappers` maps header.onlineid -> envelope.onlineid (37) | `` or `123456` | persisted by upload core when returned | | |
| HYDRO | `HYDRO` | Not set by UI (blank) / Room | `telling_headers.hydro` | `StartTellingApi` sets `hydro = ""` (73) | `` | | | |
| Hpa (luchtdruk) | `hpa` | Metadata (`etLuchtdruk`) / Room | `telling_headers.hpa` | `StartTellingApi` param `luchtdrukHpaRaw` -> hpa (44,74) | `1013.2` | decimal string | | |
| Equipment | `equipment` | Not set by UI (blank) / Room | `telling_headers.equipment` | `StartTellingApi` sets `equipment = ""` (75) | `` | | | |
| UUID | `uuid` | Generated in `StartTellingApi` | `telling_headers.uuid` | `StartTellingApi.buildEnvelopeFromUi` (76) sets `uuid = "Trektellen_Android_1.8.45_${java.util.UUID.randomUUID()}"` | `Trektellen_Android_1.8.45_abcd-1234` | | | |
| Upload tijdstip | `uploadtijdstip` | Initially set in StartTellingApi, overwritten/normalized in prepareEnvelopeForUpload | `telling_headers.uploadtijdstip` | `StartTellingApi` sets now (77); `TellingUploadCore.prepareEnvelopeForUpload` sets `uploadtijdstip = SimpleDateFormat(...)` (126-131) | `2026-07-06 09:12:05` | final upload time set during prepare/upload | | |
| nrec (aantal records) | `nrec` | Computed at prepare/upload from data length / Room | `telling_headers.nrec` | `StartTellingApi` initial `nrec = "0"` (78); `TellingUploadCore.prepareEnvelopeForUpload` sets `nrec = sanitizedData.size.toString()` (131) | `24` | number as string | | |
| nsoort (aantal soorten) | `nsoort` | Computed at prepare/upload from distinct soortid / Room | `telling_headers.nsoort` | `StartTellingApi` initial `nsoort = "0"` (79); `TellingUploadCore.prepareEnvelopeForUpload` sets `nsoort = ...size.toString()` (132) | `12` | number as string | | |
| Data (array) | `data` | From Room waarnemingen or in-memory pending records | mapped from `waarnemingen` via `RoomMappers.toServerEnvelope(records)` -> `records.map { it.toServerItem() }` | `RoomMappers.toServerEnvelope` (11-46) and `Waarneming.toServerItem` (49-77) | array of ServerTellingDataItem (see table earlier) | | | |

Referenties:
- `app\\src\\main\\java\\com\\yvesds\\vt5\\net\\StartTellingApi.kt` — `buildEnvelopeFromUi` (16-84)
- `app\\src\\main\\java\\com\\yvesds\\vt5\\features\\telling\\TellingUploadCore.kt` — `prepareEnvelopeForUpload` (114-135) en upload handling (181-276)
- `app\\src\\main\\java\\com\\yvesds\\vt5\\core\\database\\RoomMappers.kt` — header -> ServerTellingEnvelope mapping (11-46) en waarneming -> ServerTellingDataItem mapping (49-77)
 
-- Volledige lijst van JSON-velden (haarzuiver) --

Onderstaand overzicht bevat álle velden die tijdens een upload naar de server kunnen worden verzameld en doorgezonden. De tabel is georganiseerd per envelope (POST /api/counts_save) en per data-record (ServerTellingDataItem). Per veld staat de JSON-naam, corresponderende DTO property, waar het vandaan komt (UI / Room / Code), in welke Room-kolom het wordt bewaard (indien aanwezig), korte omschrijving en een voorbeeldwaarde.

### ServerTellingEnvelope (POST /api/counts_save)

| JSON naam | DTO property | Room table | Room kolom | Bron (UI / Room / Code) | Omschrijving | Voorbeeld |
|---|---|---|---|---|---|---:|
| externid | externid | telling_headers | externid | StartTellingApi / hardcoded | App-identifier / extern id in envelope | VoiceTally5 v.1.f |
| timezoneid | timezoneid | telling_headers | timezoneid | StartTellingApi / Room header | Timezone id voor timestamps | Europe/Brussels |
| bron | bron | telling_headers | bron | StartTellingApi / Room header | Broncode (app identifier) | 4 |
| _id | idLocal | (n.v.t. voor header) | (geen) | StartTellingApi / RoomMappers | Lokale id (meestal leeg bij upload) | (leeg) |
| tellingid | tellingid | telling_headers | tellingid | StartTellingApi / Room | Unieke telling id | vt5_20260706_0001 |
| telpostid | telpostid | telling_headers | telpostid | Metadata UI | Site / telpost id | site_4310 |
| begintijd | begintijd | telling_headers | begintijd | Metadata UI / Room | Begintijd in epoch sec (string) | 1657084800 |
| eindtijd | eindtijd | telling_headers | eindtijd | Metadata / afronding | Eindtijd epoch sec of leeg (live) | (leeg) |
| tellers | tellers | telling_headers | tellers | Metadata UI | Namen van tellers | J. Jansen |
| weer | weer | telling_headers | weer | Metadata UI | Samenvattende weather tekst | bewolkt, lichte regen |
| windrichting | windrichting | telling_headers | windrichting | Metadata UI | Windrichting code | NW |
| windkracht | windkracht | telling_headers | windkracht | Metadata UI | Windkracht / Beaufort | 3 |
| temperatuur | temperatuur | telling_headers | temperatuur | Metadata UI | Temperatuur (C als string) | 18 |
| bewolking | bewolking | telling_headers | bewolking | Metadata UI | Bewolking (0..8) | 4 |
| bewolkinghoogte | bewolkinghoogte | telling_headers | bewolkinghoogte | Not set / Room | Hoogte van bewolking | 1000 |
| neerslag | neerslag | telling_headers | neerslag | Metadata UI / Room | Neerslagcode | regen |
| duurneerslag | duurneerslag | telling_headers | duurneerslag | Not set / Room | Duur van neerslag | 30 |
| zicht | zicht | telling_headers | zicht | Metadata UI | Zicht in meters (string) | 5000 |
| tellersactief | tellersactief | telling_headers | tellersactief | Not set / Room | Of tellers actief zijn (intern) | (leeg) |
| tellersaanwezig | tellersaanwezig | telling_headers | tellersaanwezig | Not set / Room | Aantal aanwezige tellers / ja/nee | (leeg) |
| typetelling | typetelling | telling_headers | typetelling | Metadata UI | Type telling code | all |
| metersnet | metersnet | telling_headers | metersnet | Not set / Room | Meters net (optioneel) | (leeg) |
| geluid | geluid | telling_headers | geluid | Not set / Room | Geluid indicator/tekst | (leeg) |
| opmerkingen | opmerkingen | telling_headers | opmerkingen | Metadata UI | Vrije tekst opmerkingen | stille ochtend |
| onlineid | onlineid | telling_headers | onlineid | Code / Server response | Online id toegekend door server (kan later ingevuld worden) | 123456 |
| HYDRO | HYDRO | telling_headers | hydro | Not set / Room | Extra hydrologie veld | (leeg) |
| hpa | hpa | telling_headers | hpa | Metadata UI | Luchtdruk (hPa) | 1013.2 |
| equipment | equipment | telling_headers | equipment | Not set / Room | Gebruikte apparatuur | (leeg) |
| uuid | uuid | telling_headers | uuid | StartTellingApi / Code | UUID device / sessie | Trektellen_Android_1.8.45_abcd-1234 |
| uploadtijdstip | uploadtijdstip | telling_headers | uploadtijdstip | Prepare/upload code | Upload timestamp (format yyyy-MM-dd HH:mm:ss) | 2026-07-06 09:12:05 |
| nrec | nrec | telling_headers | nrec | Prepare/upload code | Aantal data-records in `data` array (string) | 24 |
| nsoort | nsoort | telling_headers | nsoort | Prepare/upload code | Aantal unieke soorten in telling (string) | 12 |
| data | data | (n.v.t.) | (n.v.t.) | Room / in-memory | Array van ServerTellingDataItem — zie tabel hieronder | [array] |

### ServerTellingDataItem (per waarneming / POST /api/data_save)

| JSON naam | DTO property | Room table | Room kolom | Bron (UI / Room / Code) | Omschrijving | Voorbeeld |
|---|---|---|---|---|---|---:|
| _id | idLocal | waarnemingen | idLocal | Generated / Room | Lokale record id (string) | rec_000123 |
| tellingid | tellingid | waarnemingen | tellingid | Room / header | Telling id waartoe dit record behoort | vt5_20260706_0001 |
| soortid | soortid | waarnemingen | soortid | UI / selection | Soort id (server species id) | NL:12345 |
| aantal | aantal | waarnemingen | aantal | UI / Dialog | Aantal in hoofdrichting | 7 |
| richting | richting | waarnemingen | richting | UI / exact direction | Exact direction tekst/code | NE |
| aantalterug | aantalterug | waarnemingen | aantalterug | UI / Dialog | Aantal tegenrichting | 0 |
| richtingterug | richtingterug | waarnemingen | richtingterug | UI / exact direction | Exact return direction | (leeg) |
| sightingdirection | sightingdirection | waarnemingen | sightingdirection | Annotatie / UI | Zichtrichting / compass selection | NW |
| lokaal | lokaal | waarnemingen | lokaal | UI / Dialog | Lokaal geteld aantal (niet in trek) | 0 |
| aantal_plus | aantal_plus | waarnemingen | aantal_plus | UI / Dialog | Extra plus-aantal hoofdrichting | 0 |
| aantalterug_plus | aantalterug_plus | waarnemingen | aantalterug_plus | UI / Dialog | Extra plus-aantal tegenrichting | 0 |
| lokaal_plus | lokaal_plus | waarnemingen | lokaal_plus | UI / Dialog | Extra lokaal plus | 0 |
| markeren | markeren | waarnemingen | markeren | Annotatie UI | Markeer-flag (algemeen) — 1/0 | 1 |
| markerenlokaal | markerenlokaal | waarnemingen | markerenlokaal | Annotatie UI | Markeer lokaal flag — 1/0 | 0 |
| geslacht | geslacht | waarnemingen | geslacht | Annotatie UI | Geslacht annotatie | male |
| leeftijd | leeftijd | waarnemingen | leeftijd | Annotatie UI | Leeftijd annotatie | adult |
| kleed | kleed | waarnemingen | kleed | Annotatie UI | Kleed / plumage annotatie | adult_winter |
| opmerkingen | opmerkingen | waarnemingen | opmerkingen | Annotatie UI | Vrije tekst opmerking per record | vogel dichtbij |
| trektype | trektype | waarnemingen | trektype | Annotatie UI | Type trek annotatie | passage |
| teltype | teltype | waarnemingen | teltype | Annotatie UI | Teltype annotatie | daytime |
| location | location | waarnemingen | location | Annotatie UI | Locatie annotatie (land/zee) | over_land |
| height | height | waarnemingen | height | Annotatie UI | Hoogte annotatie | high |
| tijdstip | tijdstip | waarnemingen | tijdstip | Generated / UI | Epoch seconds als string (tijd van waarneming) | 1657084823 |
| groupid | groupid | waarnemingen | groupid | Code / UI | Groeps-id voor samenhang | grp_42 |
| uploadtijdstip | uploadtijdstip | waarnemingen | uploadtijdstip | CSV import / generated | Upload timestamp / submitted | 2026-07-06 09:12:05 |
| totaalaantal | totaalaantal | waarnemingen | totaalaantal | Computed | Som van aantal + aantalterug + lokaal (string) | 7 |

---

Het bovenstaande is afgeleid uit `app\\src\\main\\java\\com\\yvesds\\vt5\\net\\Types.kt`, de Room-entities en de CSV-import/parsing code (`CsvImportManager.kt`).

Ik heb ook een CSV-bestand met dezelfde informatie aangemaakt (`api_upload_fields.csv`) in de repository-root met ";" als delimiter zodat je het eenvoudig in Excel/LibreOffice kunt openen.

