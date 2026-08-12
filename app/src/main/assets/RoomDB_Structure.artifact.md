# Room Database Structuur - VoiceTally VT5

Dit document bevat de volledige technische specificatie van de Room SQLite database voor VT5, met het oog op de ontwikkeling van de **LiteRT (TensorFlow Lite) Trainer**.

## Database Algemeen
- **Naam:** `voicetally_db`
- **Versie:** `1` (Actueel)
- **Export Schema:** Geactiveerd

---

## 1. Tabel: `telling_headers`
Bevat de metadata van elke telsessie. Cruciaal voor context-kenmerken (weer, locatie, tijd).

| Veldnaam | SQLite Type | Kotlin Type | Beschrijving |
| :--- | :--- | :--- | :--- |
| `tellingid` (PK) | TEXT | String | Uniek lokaal volgnummer (bijv. "1", "2"). |
| `onlineid` | TEXT | String | ID van de Trektellen server (indien geïmporteerd). |
| `externid` | TEXT | String | Standaard "VT5". |
| `bron` | TEXT | String | Standaard "4". |
| `telpostid` | TEXT | String | ID van de telpost. |
| `begintijd` | TEXT | String | **Starttijd in Linux Epoch (seconden).** |
| `eindtijd` | TEXT | String | Eindtijd in Linux Epoch (seconden). |
| `tellers` | TEXT | String | Namen van de waarnemers. |
| `weer` | TEXT | String | Vrije tekst weeromschrijving. |
| `windrichting` | TEXT | String | 16-traps windrichting (N, NNO, etc.). |
| `windkracht` | TEXT | String | Beaufort (0-12). |
| `temperatuur` | TEXT | String | Graden Celsius. |
| `bewolking` | TEXT | String | Bewolking in achtsten (0-8). |
| `bewolkinghoogte`| TEXT | String | Beschrijving bewolkingshoogte. |
| `neerslag` | TEXT | String | Type neerslag (geen, regen, etc.). |
| `zicht` | TEXT | String | Zichtbaarheid (bijv. in meters of code). |
| `hpa` | TEXT | String | Luchtdruk in hPa. |
| `status` | TEXT | String | `actief`, `geupload` of `gearchiveerd`. |
| `nrec` | TEXT | String | Totaal aantal records in deze sessie. |
| `nsoort` | TEXT | String | Totaal aantal unieke soorten in deze sessie. |

---

## 2. Tabel: `waarnemingen`
Bevat de individuele vogelwaarnemingen. Dit is de primaire dataset voor training.

| Veldnaam | SQLite Type | Kotlin Type | Beschrijving |
| :--- | :--- | :--- | :--- |
| `idLocal` (PK) | TEXT | String | UUID voor lokale uniekheid. |
| `tellingid` (PK/FK)| TEXT | String | Koppeling naar `telling_headers`. |
| `onlineid` | TEXT | String | ID van de individuele record op de server. |
| `soortid` | TEXT | String | **Unieke soort-ID (Label voor de AI).** |
| `aantal` | TEXT | String | Aantal vogels in hoofdrichting (`direction1`). |
| `aantalterug` | TEXT | String | Aantal vogels in tegenrichting (`direction2`). |
| `richting` | TEXT | String | Hoofdrichting (tekst). |
| `tijdstip` | TEXT | String | **Exacte waarnemingstijd in Epoch (seconden).** |
| `leeftijd` | TEXT | String | Optionele annotatie. |
| `geslacht` | TEXT | String | Optionele annotatie. |
| `kleed` | TEXT | String | Optionele annotatie. |
| `opmerkingen` | TEXT | String | Vrije tekst bij de waarneming. |
| `totaalaantal` | TEXT | String | Som van aantal + aantalterug. |

---

## 3. Tabel: `weather_archive`
Historische uurlijkse weergegevens opgehaald via Open-Meteo. Gebruikt voor verrijking.

| Veldnaam | SQLite Type | Kotlin Type | Beschrijving |
| :--- | :--- | :--- | :--- |
| `locationId` (PK)| TEXT | String | Telpost ID of Cluster ID. |
| `timeEpoch` (PK) | INTEGER | Long | **Unix timestamp (uurbasis).** |
| `temp` | REAL | Double? | Temperatuur op 2m hoogte. |
| `windSpeed10m` | REAL | Double? | Windsnelheid (m/s). |
| `windDir10m` | REAL | Double? | Windrichting (graden 0-360). |
| `pressureMsl` | REAL | Double? | Luchtdruk op zeeniveau (hPa). |
| `cloudCover` | REAL | Double? | Bewolking in percentage (0-100%). |
| `precip` | REAL | Double? | Neerslag in mm. |

---

## 4. Tabel: `ai_logs`
Geschiedenis van prognoses en feedback. Essentieel voor "Reinforcement Learning".

| Veldnaam | SQLite Type | Kotlin Type | Beschrijving |
| :--- | :--- | :--- | :--- |
| `id` (PK) | INTEGER | Int | Auto-increment. |
| `tellingid` | TEXT | String | Koppeling naar de betreffende sessie. |
| `timestamp` | INTEGER | Long | Tijdstip van de prognose. |
| `type` | TEXT | String | "metadata" of "forecast". |
| `requestContext` | TEXT | String | **JSON-blob met alle input-features.** |
| `suggestions` | TEXT | String | JSON-blob met de top suggesties en kansen. |
| `rating` | INTEGER | Int | Gebruikersscore (0-5 sterren). |
| `feedback` | TEXT | String | Tekstuele opmerking van de gebruiker. |

---

## Belangrijke opmerkingen voor LiteRT
1. **Types:** Merk op dat in `telling_headers` en `waarnemingen` veel numerieke velden als `TEXT` zijn opgeslagen voor server-compatibiliteit. Tijdens het preprocessen voor LiteRT moeten deze via `CAST(... AS FLOAT)` of Kotlin's `toFloat()` worden omgezet.
2. **Features:** De meest waardevolle features voor het model zijn: `telpostid`, `month`, `dayOfYear`, `hour`, `windrichting`, `windkracht`, `temperatuur` en `luchtdruk`.
3. **Labels:** Het doel (target) is `soortid`.
