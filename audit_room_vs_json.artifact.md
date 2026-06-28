# Audit: Room vs JSON Route Consistentie

**Status:** Analyse voltooid
**Datum:** 2024-05-22

## 1. Conclusie
Na grondige inspectie van de netwerk-modellen, de Room-entiteiten en de mappers, kan ik bevestigen dat de Room-route **structureel identiek** is aan de JSON-route. Een waarneming die uit Room wordt opgehaald en omgezet via de `HybridTellingRepository`, zal exact dezelfde JSON-payload genereren voor de Trektellen API als de huidige JSON-route.

## 2. Bevindingen per onderdeel

### A. Data-integriteit (Velden)
*   De `Waarneming` (Room Entity) bevat exact dezelfde 26 velden als `ServerTellingDataItem` (Netwerk model).
*   Alle velden in Room zijn gedefinieerd als `String`. Dit is cruciaal omdat de JSON-route ook alles als String behandelt. Er vindt dus geen ongewenste type-conversie plaats (zoals van "01" naar 1) die de server-validatie zou kunnen verstoren.
*   De mapper `Waarneming.toServerItem()` voert een 1-op-1 kopie uit van alle velden.

### B. Metadata (Header)
*   De `TellingHeader` (Room Entity) komt overeen met de velden in `ServerTellingEnvelope`.
*   Velden zoals `onlineid`, `tellingid`, `begintijd`, `eindtijd` en alle omgevingsfactoren (wind, weer, etc.) zijn aanwezig.
*   **Aandachtspunt:** In `TellingHeader.kt` zijn velden zoals `nrec` en `nsoort` aanwezig. Bij de JSON-route worden deze vaak berekend op het moment van uploaden. De `HybridTellingRepository` moet ervoor zorgen dat deze totalen in Room altijd up-to-date zijn wanneer een sessie wordt heropend of geüpload vanuit de database.

### C. Record ID's (`_id`)
*   Zowel de JSON-route als de Room-route maken gebruik van `DataUploader.getAndIncrementRecordId()`.
*   Omdat dit mechanisme werkt op basis van `SharedPreferences` (gekoppeld aan het `tellingId`), blijft de nummering consistent, ongeacht welke opslagmethode op dat moment actief is.

### D. Parallelle route
*   De `HybridTellingRepository` is zo ontworpen dat bij de `PARALLEL` modus de data naar beide locaties wordt geschreven.
*   Omdat de Room-entiteit een directe afspiegeling is van het netwerk-item dat ook voor de JSON-backup wordt gebruikt, is er geen risico op divergentie tussen de twee backups.

## 3. Aanbevelingen (voor latere fase)
Hoewel de data-structuur identiek is, zijn er twee proces-checks die ik adviseer bij het definitief overschakelen naar Room:
1.  **Status-beheer:** De Room-route introduceert een `status` veld (`actief`, `geupload`, `gearchiveerd`). De JSON-route werkt met fysieke mappen (`active_telling.json` vs exports). Zorg dat de logica voor "Wat is een actieve telling?" in beide routes hetzelfde blijft.
2.  **Bulk-upload:** De `DataUploader` is momenteel geoptimaliseerd voor `uploadSingleObservation`. Bij het uitlezen uit Room voor een "Afronden" actie, moet de volledige lijst (`List<Waarneming>`) correct worden omgezet naar de `data` array in de `ServerTellingEnvelope`.

**Eindoordeel:** De architectuur is gereed voor identieke resultaten. Geen codewijzigingen nodig op basis van deze audit.

---

## 4. Audit: Database Beheer UI Structuur

Naast de data-audit heb ik ook de UI-structuur van het Database Beheer gecontroleerd op basis van de gevraagde flow.

### A. Navigatie Flow (Lijst -> Detail)
*   **Database Beheer (Hoofdscherm):** De knop `btnTellingenLijst` opent de `DatabaseTellingLijstActiviteit`. Dit is de centrale toegangspoort.
*   **Sessie Overzicht:** In `DatabaseTellingLijstActiviteit` wordt nu een lijst getoond met alle opgeslagen tellingen. Deze lijst gebruikt het nieuwe `item_db_telling_sessie.xml` (met card-layout). Pas na het aantikken van een item in deze lijst wordt de detailpagina geopend.
*   **Detailoverzicht:** De `DatabaseTellingDetailActiviteit` toont de specifieke gegevens van de gekozen telling.

### B. Layout van de Detailpagina
*   **Metadata Card:** Bovenaan het scherm staat de metadata nu volledig gegroepeerd in een `MaterialCardView` (`vt5_dark_gray` met `vt5_light_blue` rand).
*   **Waarnemingen Lijst:** Daaronder verschijnen alle vogelwaarnemingen (`ALLE waarnemingen`) onder elkaar, gebruikmakend van het nieuwe `item_db_waarneming.xml`.
*   **Scrollgedrag:** De volledige pagina (zowel de metadata-card als de lijst met waarnemingen) is geplaatst in een `NestedScrollView`. Dit zorgt ervoor dat de gebruiker vloeiend door de hele telling kan scrollen.

### C. Tablet (sw600dp) Optimalisatie
*   Op tablets (sw600dp) wordt de metadata in de card niet simpelweg onder elkaar getoond, maar in een `FlexboxLayout`. Hierdoor worden de velden naast elkaar geplaatst (meerdere kolommen), wat perfect gebruikmaakt van de extra schermbreedte terwijl de logische volgorde behouden blijft.

**Conclusie UI Audit:** Het systeem voldoet nu exact aan de beschreven wens: eerst een lijst van alle tellingen, en bij selectie een detailoverzicht met metadata in een card en daaronder de waarnemingen in één scrollbare view.

---

## 5. Audit: Wijzigingen terugsturen naar Server (Push)

Ik heb de functionaliteit toegevoegd om wijzigingen die in de database zijn aangebracht, veilig terug te sturen naar de server.

### A. Mechanisme
*   **Volledige Enveloppe:** Er is een nieuwe knop toegevoegd op het detailscherm: **"Sync: Alle wijzigingen naar Server pushen"**.
*   **Data Verzameling:** Bij het indrukken van deze knop haalt de app alle waarnemingen voor die specifieke sessie uit Room op.
*   **Mapper:** De `toServerEnvelope()` mapper combineert de metadata van de `TellingHeader` met de lijst van `Waarneming` objecten tot een valide `ServerTellingEnvelope`.

### B. Veiligheid en Consistentie
*   **Sanitisering:** De enveloppe gaat door de centrale `TellingUploadCore.prepareEnvelopeForUpload()`. Dit garandeert dat alle velden (zoals totalen `nrec` en `nsoort`) herberekend worden op basis van de nieuwe data voordat ze verzonden worden.
*   **Status Update:** Na een succesvolle push wordt de status van de telling in de database bijgewerkt en wordt de eventueel nieuwe `onlineid` van de server weer lokaal opgeslagen.
*   **Identificatie:** Omdat we de originele `uuid` en `onlineid` (indien aanwezig) meesturen, herkent de server dit als een update van de bestaande sessie in plaats van een dubbele invoer.

**Conclusie Push-functie:** Gebruikers kunnen nu correcties doorvoeren in de lokale database en deze met één druk op de knop synchroniseren met Trektellen.nl. Dit maakt de Room-route even krachtig als de oorspronkelijke editor-flow in de JSON-route.