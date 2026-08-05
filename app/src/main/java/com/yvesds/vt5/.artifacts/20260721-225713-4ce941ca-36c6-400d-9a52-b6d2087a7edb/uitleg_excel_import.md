# Intelligent Excel Batch-Import Systeem

Dit document beschrijft de werking van de nieuwe `ExcelImportManager` die specifiek is ontworpen voor het foutloos importeren van Trektellen-gegevens in de Room database.

## 1. Doel van de Importer
Het systeem vervangt de oude CSV-import door een directe Excel (.xlsx) parser. Dit garandeert betere data-integriteit en voorkomt problemen met separators en text-encoding.

## 2. Bestands-Paren en Koppeling
De app scant de map `VT5/imports` op paren:
- **Header**: `Trektellen_headerdata_{site}_{year}.xlsx`
- **Data**: `Trektellen_data_{site}_{year}.xlsx`

### De Link (id <-> countid)
- In de **Header sheet** gebruiken we de kolom `id` als unieke identificatie van de sessie.
- In de **Data sheet** gebruiken we de kolom `countid` om waarnemingen te linken aan de juiste header.
- Deze waarden worden opgeslagen als `onlineid` in Room voor toekomstige de-duplicatie.

## 3. Slimme De-duplicatie
Voordat een record wordt toegevoegd, voert de app een "Pre-flight check" uit:
- Bestaat de `onlineid` (van de header of waarneming) al in de database?
- Zo ja: De rij wordt volledig overgeslagen. Dit minimaliseert verwerkingstijd en voorkomt dubbele waarnemingen.

## 4. Elegante Timestamp Fallback
Voor telposten die niet "live" invoeren, kan het veld `timestamp` in de data-sheet leeg zijn.

**Oplossing**:
1. Indien `timestamp` leeg is, zoekt de app de bijbehorende header (via `countid`).
2. De app kijkt naar de kolom `stop` in de header-rij.
3. De app extraheert het uur uit `stop` (ongeacht of dit in epoch-stijl of datum-tijd notatie is).
4. Dit uur wordt geformatteerd als `uu:mm:ss` en toegewezen aan de waarneming.

## 5. Datum-foutloosheid
Excel datums (bijv. 45312.4) worden via een gespecialiseerde converter omgezet naar Unix Epoch (seconden). Indien de kolom `Epoch_tijdstip` aanwezig is in de data-file, krijgt deze voorrang voor 100% nauwkeurigheid.

## 6. Technische Implementatie
- **Bibliotheek**: Apache POI (gestroomlijnd voor Android).
- **Transacties**: Elke jaar-set wordt binnen één database-transactie verwerkt. Bij een fout wordt de hele set teruggedraaid.
- **Feedback**: De gebruiker ziet een real-time ticker van het aantal geïmporteerde vs. overgeslagen records.
