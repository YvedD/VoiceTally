# Implementatieplan: Hybride Room-JSON Systeem

Dit document beschrijft de stappen om de VT5 app om te vormen naar een systeem waar de **Room Database** de primaire bron is voor actieve tellingen, en **JSON** uitsluitend wordt gebruikt voor transport naar de server en archivering.

## 1. Kernprincipe
*   **Counting Fase:** Elke nieuwe waarneming of wijziging wordt *direct en uitsluitend* in Room opgeslagen. Geen constante SAF/JSON schrijfacties meer.
*   **Recovery Fase:** Bij het herstarten van de app wordt de sessie hersteld door de database te pollen op basis van de actieve `tellingid`.
*   **Upload Fase:** De benodigde JSON-enveloppe wordt "Just-in-Time" opgebouwd vanuit de database op het moment dat de gebruiker op 'Afronden' klikt.

---

## 2. Aanpassingen per Bestand

### A. `HybridTellingRepository.kt` (De Snelheids-fix)
Dit is de belangrijkste stap voor prestatiewinst.
*   **Actie:** Verwijder de aanroep `exportDatabaseToSaf()` uit de methoden `saveWaarnemingToRoom` en `saveHeaderToRoom`.
*   **Reden:** Deze methode blokkeert de snelheid van SQLite door telkens de trage SAF-omweg te nemen.
*   **Nieuwe methode:** Voeg een methode `forceDatabaseBackup()` toe die handmatig kan worden aangeroepen bij belangrijke momenten (bijv. bij het afsluiten van de app of na een upload).

### B. `RecordsBeheer.kt` (Verwijderen JSON-afhankelijkheid)
*   **Actie:** De methode `persistIndex()` en alle logica rondom `pending_index.json` mag worden verwijderd.
*   **Actie:** In `collectFinalAsRecord` moet de focus volledig verschuiven naar `hybridRepository.saveWaarnemingToRoom(item)`.
*   **Actie:** De lijst `_pendingRecordsFlow` in het geheugen blijft bestaan voor de UI, maar wordt bij initialisatie gevuld vanuit Room in plaats van uit een JSON-bestand.

### C. `TellingScherm.kt` (UI Synchronisatie)
*   **Actie:** Verwijder de methode `persistEnvelopeAsync()`. Deze zorgt nu voor de vertragende overschrijving van `active_telling.json`.
*   **Actie:** Zorg dat elke wijziging in de metadata (bijv. via een dialoog in het tellingscherm) direct een `hybridRepository.saveHeaderToRoom()` triggert.

### D. `TellingInitializer.kt` (Het Nieuwe Herstel)
Momenteel zoekt de initializer naar `active_telling.json`.
*   **Actie:** Wijzig de recovery-logica. Haal de actieve `tellingid` op uit `AppDataStore`.
*   **Actie:** Roep `hybridRepository.getWaarnemingenList(id)` aan om alle records op te halen.
*   **Actie:** Gebruik deze lijst om de `TegelBeheer` en de `SpeechLog` weer op te bouwen.

### E. `TellingAfrondHandler.kt` (Just-in-Time Enveloppe)
Dit bestand wordt de "bruggenbouwer" tussen Room en de Server.
*   **Actie:** In plaats van het inladen van `pref_saved_envelope_json`, bouwt deze handler nu een nieuwe enveloppe op.
*   **Stappen in de code:**
    1.  `val header = database.tellingDao().getHeader(activeId)`
    2.  `val records = database.tellingDao().getWaarnemingenList(activeId)`
    3.  `val envelope = header.toServerEnvelope(records)`
*   **Actie:** Geef deze vers gegenereerde `envelope` door aan `TellingUploadCore`.

---

## 3. Implementatie Volgorde (Stappenplan)

1.  **Fase 1: Database-only Counting**
    *   Schakel `exportDatabaseToSaf()` uit in de repository.
    *   Stop het continu schrijven van `active_telling.json` in `TellingScherm`.
    *   *Resultaat: Invoeren van vogels is nu instant (geen lag).*

2.  **Fase 2: Robuuste Recovery**
    *   Pas `TellingInitializer` aan om de database als bron te gebruiken bij een herstart.
    *   Test dit door de app hard af te sluiten tijdens een telling en te kijken of de records terugkomen uit Room.

3.  **Fase 3: Clean Upload**
    *   Pas `TellingAfrondHandler` aan om de JSON-enveloppe on-the-fly te genereren.
    *   Behoud de optie om ná een succesvolle upload alsnog een JSON-bestand in de `exports` map te schrijven voor de gebruiker (als historisch archief).

4.  **Fase 4: Status Management**
    *   Zorg dat na een succesvolle upload de status van de header in Room op `geupload` wordt gezet.
    *   Wis de `active_telling_id` in `AppDataStore` zodat de initializer de volgende keer met een schoon scherm start.

---

## 4. Risico's en Monitoring
*   **Migratie:** Bestaande gebruikers hebben nog een `active_telling.json`. De eerste keer moet de app dit bestand éénmalig inlezen en naar Room migreren voordat het hybride systeem volledig overneemt.
*   **Zichtbaarheid:** Omdat de database niet meer continu naar SAF wordt geëxporteerd, ziet de gebruiker in de 'Documents' map niet direct zijn actuele stand. Dit moet duidelijk gecommuniceerd worden of opgevangen worden door een handmatige 'Backup naar Documenten' knop.

**Eindoordeel:** Dit systeem maakt de app aanzienlijk sneller en moderner, terwijl de bewezen betrouwbaarheid van het JSON-formaat voor server-communicatie behouden blijft.
