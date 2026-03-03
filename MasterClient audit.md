# Master-client audit: benodigde bestanden en layout-aanpassingen

## Doel van deze audit
Inventariseren **hoeveel nieuwe bestanden** (en welke types) nodig zijn om de huidige app te evolueren naar een master-client systeem, plus welke bestaande onderdelen geraakt worden. Dit is een ontwerp-audit, **geen code**.

## Samenvatting in het kort
- **Nieuwe bestanden (verwachting): 14–22 stuks**
  - 7–11 Kotlin files (core master/client, netwerk, protocol)
  - 3–5 layout XML’s (master/client UI + pairing)
  - 2–4 drawable/menu/strings resources
- **Bestaande bestanden**: meerdere schermen worden uitgebreid (TellingScherm, Instellingen, Installatie) maar blijven functioneel identiek in solo‑modus.

## Kernprincipes (ontwerp)
- **Master = enige uploader** naar de server.
- **Clients** sturen enkel lokale events naar master.
- **Solo‑modus blijft default**; master‑client is opt‑in.
- **Event‑sourcing** met correction‑events voor late wijzigingen.
- **LAN‑first** (hotspot gegarandeerd), QR/PIN pairing.

---

## Nieuwe bestanden (Kotlin / core)
Onderstaande lijst is de **aanbevolen set** nieuwe bestanden om de architectuur schoon en onderhoudbaar te houden.

### 1) Netwerk / transportlaag (LAN)
1. `features/master/LocalServer.kt`
   - Start/stop lokale server (WebSocket of gRPC) op master.
2. `features/client/LocalClient.kt`
   - Verbindt naar master en verstuurt events; retry‑queue.
3. `features/common/DiscoveryService.kt`
   - mDNS/NSD discovery + fallback (manuele IP).
4. `features/common/PairingManager.kt`
   - QR/PIN pairing tokens, time‑boxed sessies.

### 2) Protocol / berichten
5. `features/common/protocol/MasterClientProtocol.kt`
   - Dataclasses voor event, ack, correction, heartbeat.
6. `features/common/protocol/ProtocolCodec.kt`
   - (De)serialisatie van events (JSON/CBOR).

### 3) Event‑workflow en deduplicatie
7. `features/master/MasterEventProcessor.kt`
   - Dedup op `clientEventId`, aanmaak records, totalen update.
8. `features/client/ClientEventQueue.kt`
   - Lokale queue + resend tot ACK.
9. `features/common/EventIdGenerator.kt`
   - Unieke event IDs per client.

### 4) Integratie met bestaande tellingflow
10. `features/master/MasterSessionManager.kt`
    - Start/stop master‑sessie, gekoppelde clients beheren.
11. `features/client/ClientSessionManager.kt`
    - Status bijhouden (verbonden/offline/pending).

### 5) Correcties / annotaties
12. `features/common/CorrectionEventHandler.kt`
    - Correcties koppelen aan target event.

### 6) UI‑state en observability
13. `features/master/MasterStatusViewModel.kt`
14. `features/client/ClientStatusViewModel.kt`

**Totaal Kotlin-bestanden: ~14**

---

## Nieuwe resources (layout/strings/drawables)
### Layouts (3–5)
1. `res/layout/dialog_pairing_master.xml`
   - QR + PIN tonen, “sluit pairing” knop.
2. `res/layout/dialog_pairing_client.xml`
   - QR scanner of IP/PIN invoer.
3. `res/layout/item_client_status.xml`
   - List item met clientnaam, status, laatste sync.
4. `res/layout/bottomsheet_master_status.xml` (optioneel)
   - Snelle master‑status en verbonden clients.

### Strings (1)
5. `res/values/strings_master_client.xml`
   - Labels en berichten (master‑modus, client‑modus, errors).

### Drawables/Menus (0–2)
6. `res/menu/master_client_menu.xml` (optioneel)
   - “Add clients”, “Stop master”, “Reconnect”.
7. Extra iconen voor master/client status (optioneel).

**Totaal nieuwe resources: 5–8**

---

## Bestaande bestanden die aangepast moeten worden
### Schermen
- `features/telling/TellingScherm.kt`
  - Master‑knop “Add clients” + status indicator.
- `features/hoofd/InstellingenScherm.kt`
  - Opt‑in master‑modus, client‑modus, pairing instellingen.
- `features/opstart/ui/InstallatieScherm.kt`
  - Uitleg master‑client, optionele discovery‑toggle.

### Logica / data
- `features/telling/RecordsBeheer.kt`
  - Aanvaarden van externe events (van clients) als records.
- `features/telling/TellingUiManager.kt`
  - Live updates bij inkomende client events.

### Netwerk / upload
- `features/network/DataUploader.kt`
  - Ongewijzigd voor serverupload (master only), maar triggerpunt
    verschuift naar master‑flow.

---

## Layout‑aanpassingen (overzicht)
1. **TellingScherm**
   - Nieuwe knop: “Add clients” (alleen zichtbaar in master‑modus).
   - Statusindicator (bijv. “3 clients verbonden”).
2. **InstellingenScherm**
   - Toggle: “Master‑modus” / “Client‑modus”.
   - Pairing‑instellingen (PIN lengte, discovery timeout).
3. **InstallatieScherm**
   - Korte uitleg master‑client + testknop voor LAN‑scan.

---

## Waarom deze bestanden nodig zijn
- **Separatie van verantwoordelijkheden:** server/client, protocol, pairing en event‑processing moeten losgekoppeld zijn van TellingScherm.
- **Onderhoudbaarheid:** latere uitbreiding naar Wi‑Fi Direct of Bluetooth is eenvoudiger.
- **Stabiliteit:** solo‑modus blijft intact en testbaar.

---

## Verwachte impact op teststrategie
- **Unit tests:** protocol codering/decodering, deduplicatie, event‑queue.
- **Integratie tests:** client‑event → master → TellingScherm update.
- **Fallback tests:** offline scenario, retry en herstel.

---

## Totale schatting nieuwe bestanden
- **Kotlin:** 14 (range 10–18)
- **Layouts:** 3–5
- **Strings/Menu/Drawables:** 2–4

**Totaal:** ~19–27 nieuwe bestanden.

---

## Opmerking
De exacte aantallen hangen af van:
- gekozen transport (WebSocket vs gRPC),
- keuze voor QR‑scan (extra camera‑component),
- gebruik van Play Services (Nearby),
- niveau van UI‑uitbreiding (simple vs uitgebreid statusbeheer).

