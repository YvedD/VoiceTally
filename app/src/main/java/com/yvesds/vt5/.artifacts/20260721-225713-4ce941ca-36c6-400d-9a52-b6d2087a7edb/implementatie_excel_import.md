# Documentatie: Intelligent Excel Batch-Import Systeem (Versie 1.0)

Dit document legt de werking uit van de nieuw geïmplementeerde Excel-importeur, die specifiek is gebouwd voor het verwerken van Trektellen-exportbestanden.

## 1. Nieuwe Componenten
- **`ExcelImportManager.kt`**: De kern-engine die `.xlsx` bestanden leest en vertaalt naar Room entiteiten.
- **`Apache POI 5.2.5`**: Toegevoegd als bibliotheek om directe Excel-toegang mogelijk te maken zonder CSV-tussenstappen.
- **`DatabaseBeheerScherm.kt`**: Bijgewerkt met een automatische "Pairing Engine" die bestanden in de `VT5/imports` map koppelt.

## 2. Het Pairing Systeem
De app zoekt niet langer naar losse bestanden, maar naar **paren**. Een paar wordt herkend aan de hand van de bestandsnaam:
- `Trektellen_headerdata_{telpost}_{jaar}.xlsx`
- `Trektellen_data_{telpost}_{jaar}.xlsx`

Alleen als beide bestanden aanwezig zijn voor een specifiek jaar en telpost, wordt de import aangeboden in de lijst.

## 3. Verbindingslogica (id <-> countid)
De koppeling tussen tellingen (headers) en individuele records (waarnemingen) is nu 100% waterdicht:
1. De kolom **`id`** uit het header-bestand wordt opgeslagen als `onlineid`.
2. Bij het inlezen van de waarnemingen wordt gekeken naar kolom **`countid`**. 
3. De app zoekt de header die als `onlineid` diezelfde `countid` heeft en koppelt ze in de database via de `tellingid` (UUID).

## 4. De "Elegante" Timestamp Fallback
Zoals gevraagd, gaat de app intelligent om met ontbrekende tijdstippen bij niet-live tellingen:
- Indien het veld `timestamp` in de data-sheet leeg is:
    - De app zoekt de bijbehorende header-rij op via de `id`.
    - De app haalt de waarde uit de kolom **`stop`** (eindtijd van de telling).
    - De app extraheert hieruit het uur (bijv. "10:00:00") en wijst dit toe aan de waarneming.
- **Prioriteit**: Indien de kolom `Epoch_tijdstip` aanwezig is, wordt deze gebruikt voor maximale nauwkeurigheid.

## 5. De-duplicatie & Snelheid
- **Headers**: Worden overgeslagen als de `onlineid` al in de database staat.
- **Waarnemingen**: Worden overgeslagen op basis van hun unieke `dataid` (onlineid).
- **Transacties**: Data wordt in blokken weggeschreven voor maximale snelheid (bulk-inserts).

## 6. Verificatie (Debug-Alleen)
Bij een succesvolle import verschijnt er tijdelijk een **Import Controle [DEBUG]** venster. 
- Dit venster leest de data **direct uit de database tabellen** (niet uit het bestand).
- Hiermee kun je controleren of de `stop`-tijd correct is gebruikt als fallback voor ontbrekende waarnemingstijdstippen.

## 7. Gebruik
1. Plaats de Excel-bestanden in de map `VT5/imports`.
2. Ga in de app naar **Databasebeheer**.
3. Klik op **Batch Import (VT5/imports)**.
4. Controleer de gevonden paren en klik op **Start Import**.
