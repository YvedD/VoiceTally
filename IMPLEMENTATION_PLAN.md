# Implementation Plan: Offline Data-Sync & P2P Architectuur voor VoiceTally (VT5)

Dit document beschrijft de gedetailleerde technische specificaties, architectuurpatronen, permissie-afhandeling, Foreground Service setup en de integratie met de reeds aanwezige codebasecomponenten voor de offline P2P synchronisatie in VoiceTally.

---

## 1. Analyse van Reeds Aanwezige Codebase Componenten (`features/masterClient`)

Uit inspectie van de codebase blijkt dat er reeds een solide fundering aanwezig is voor master-client communicatie en netwerkprotocollen:
- **`features/masterClient/protocol/McProtocol.kt`**: Bevat de gestandaardiseerde protocolberichten (`McEnvelope`, `PairingRequest`, `PairingResponse`, `ObservationEvent`, `AckMessage`, `ExportDataMessage`, `LeaveMessage`, etc.).
- **`features/masterClient/McQrPayload.kt`**: Bevat de `McQrPayload` en `McQrPayloadCodec` voor het coderen en decoderen van koppelingsgegevens via QR-code (`VT5MC:...`).
- **`features/masterClient/MasterServer.kt` & `ClientConnector.kt`**: Beheren de netwerkverbindingen en event-wachtrijen.
- **`core/database/entities/SyncLog.kt`**: Biedt een reeds gedefinieerde Room entiteit om upload-statussen en serverantwoorden bij te houden, volledig gescheiden van de kerntabellen.

---

## 2. Cruciale Scheiding: Spraakinvoer (Internet) vs. P2P Master-Client Sync

- **Spraakinvoer:** Kan op individuele teller-toestellen afhankelijk zijn van internettoegang (bijv. voor cloud-gebaseerde spraakherkenning / Speech-to-Text API's). Dit staat echter **volledig los** van het onderlinge P2P netwerk.
- **P2P Master-Client Synchronisatie:** Verloopt volledig lokaal en ad-hoc via Wi-Fi/Nearby Connections tussen de master en de tellers. Het P2P-netwerk vereist geen internettoegang of actieve router, ongeacht of een individueel toestel wel of geen internet heeft voor spraakinvoer.

---

## 3. Maximale Betrouwbaarheid & Open-Veld Bereik Optimalisatie

Om in open veld (polders, duinen, open vlaktes) de **hoogst mogelijke betrouwbaarheid** en het **grootste bereik** te garanderen, worden de volgende architecturale maatregelen toegepast:

### A. Radiofrequentie & Bandbreedte Optimalisatie (2.4 GHz vs 5 GHz)
- In open veld biedt de **2.4 GHz Wi-Fi band** (die Nearby Connections onder water gebruikt voor socket-transmissie) een significant groter fysiek bereik en betere buiging om lichte obstakels (zoals rietkragen, dijken of kleine glooiingen) dan 5 GHz.
- De app forceert of prioriteert stabiele 2.4 GHz verbindingen waar mogelijk via de transportconfiguratie.

### B. Mitigatie van Range Limieten & Reconnection Fallback
- **Open-veldbereik tot 30-50+ meter:** Dankzij de combinatie van BLE discovery en Wi-Fi direct sockets blijft de verbinding stabiel over typische telpost-afstanden.
- **Naadloze Offline Buffer (`ClientEventQueue` / `SyncLog`):**
  - Wanneer een teller buiten bereik raakt of de verbinding wegvalt, schakelt de app onmerkbaar over naar volledig lokaal tellen.
  - Alle observaties worden direct lokaal weggeschreven in de event queue en `SyncLog` (`success = "0"`).
- **Automatische Resync & ACK-bevestiging:**
  - Zodra de verbinding herstelt (`onConnectionResult`), triggert de `ClientConnector` automatisch een `ExportDataMessage` of een batch van openstaande `ObservationEvent`s.
  - De Master verwerkt de events, controleert op unieke `clientEventId`s (idempotentie en deduplicatie) en stuurt een `AckMessage` terug.
  - Na ontvangst van de ACK markeert de client de records definitief als gesynchroniseerd (`success = "1"`), zodat geen enkele telling verloren gaat.

---

## 4. Room Database Schema Behoud
**CRITICAL RULE:** De bestaande Room entiteiten en tabellen (zoals `tellingen`, `soorten`, `protocollen`) blijven **100% ongewijzigd**. Synchronisatie en status-tracking verlopen uitsluitend via de reeds aanwezige `SyncLog` entiteit en event queues.

---

## 5. QR-Code Koppeling & CameraX Scanner

De reeds aanwezige `McQrPayloadCodec` (`VT5MC:...`) wordt gebruikt om de verbindingsparameters (IP, poort, sessietoken) te coderen in een QR-code op de Master. De Tellers scannen deze QR-code via CameraX om direct en zonder openbare broadcasting te koppelen.

---

## 6. P2P Foreground Service & Android 13+ Compliance

Voor langdurige velddagen (8+ uur) draait een `P2PForegroundService` met een persistente notificatie om te voorkomen dat het Android besturingssysteem de netwerk-sockets of achtergrondthreads termineert.
