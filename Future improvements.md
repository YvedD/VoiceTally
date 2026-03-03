# Future improvements: onsite master-client telling (audit)

## Task receipt
This document audits whether a local master-client setup is feasible in Android/Kotlin and outlines a future architecture. No current code is modified.

## Executive summary
Yes, a master-client setup is feasible on Android/Kotlin. The most practical future path is a local Wi-Fi master (phone/tablet) that hosts a small LAN server, with client devices connecting on-site to submit observations. The master remains the only device that uploads to the server (to respect the “one active telling” rule). Bluetooth can be a fallback transport, but it is slower and more complex for multi-client streaming.

## Current app baseline (from codebase)
- **Server upload flow:**
  - `DataUploader.uploadSingleObservation()` posts a single `ServerTellingDataItem` to `/api/data_save/{onlineId}`.
  - `AfrondWorker` builds a full `ServerTellingEnvelope` and uploads it (counts_save).
- **Local persistence and recovery:**
  - `RecordsBeheer` keeps a pending list + persists an index + per-record backups.
  - Envelope JSON is stored in SharedPrefs (`pref_saved_envelope_json`).
- **One active telling:**
  - `ServerTellingEnvelope` includes `onlineid`, and all uploads assume one active server session.
- **Permissions:**
  - Manifest includes `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`, but there is **no Bluetooth usage** in code now.

Implication: a master device can remain the only uploader while clients contribute local observations that are forwarded to the master.

## Feasibility in Android/Kotlin
- **Possible:** local networking is fully supported (Wi-Fi LAN, Wi-Fi Direct, Bluetooth sockets/BLE, Nearby Connections).
- **Main constraints:** background execution limits, discovery, pairing, security, and reliability under poor connectivity.
- **Server constraint:** keep server uploads on the master only; clients never upload directly.

## Transport options (comparison)
### 1) Wi-Fi LAN (same access point or master hotspot)
- **Pros:** high throughput, low latency, easy multi-client.
- **Cons:** requires same SSID or master hotspot.
- **Discovery:** mDNS/NSD or QR code with IP+port.
- **Recommended:** Yes (primary transport).

### 2) Wi-Fi Direct
- **Pros:** no external router needed.
- **Cons:** more complex UX; group owner changes; can be unstable across devices.
- **Discovery:** Wi-Fi Direct service discovery.
- **Recommended:** Optional fallback if no shared Wi-Fi.

### 3) Bluetooth Classic (RFCOMM)
- **Pros:** works without Wi-Fi, okay for 1–2 clients.
- **Cons:** limited bandwidth, multi-client complexity, pairing friction.
- **Recommended:** Only as fallback for small setups.

### 4) BLE (GATT)
- **Pros:** low power.
- **Cons:** not ideal for streaming logs; limited MTU.
- **Recommended:** Not ideal for this use case.

### 5) Nearby Connections (Google Play services)
- **Pros:** handles discovery + transport selection (BT/Wi-Fi), good UX.
- **Cons:** dependency on Play services; non-Play devices may fail.
- **Recommended:** Optional if Play services are acceptable.

## Recommended approach
- **Primary:** Wi-Fi LAN server hosted by master device.
- **Fallback:** Wi-Fi Direct or Bluetooth Classic.
- **Discovery:** mDNS/NSD + QR code fallback.
- **Security:** pairing token + app-level auth; TLS optional but recommended.

## Proposed future architecture
### Roles
- **Master device:**
  - Starts the telling and receives the official `onlineid`.
  - Hosts a local server (WebSocket or gRPC).
  - Assigns record IDs and updates its UI in real time.
  - Uploads to server only from master.

- **Client device(s):**
  - Connect to master and submit “observation events.”
  - Do not upload to server directly.
  - Keep a local retry queue in case of temporary disconnection.

### Protocol (local, on-site)
- **Event message (client → master):**
  - Use a subset of `ServerTellingDataItem` + metadata:
    - `soortid`, `aantal`, `aantalterug`, `tijdstip`, optional annotations.
  - Client sends a `clientEventId` for deduplication.
- **Master response:**
  - `ack` with assigned `idLocal`, current totals, and optional errors.
- **Master broadcast:**
  - Updated totals and log lines for all clients.

### Data flow
1. **Master starts telling** → gets `onlineid`.
2. **Clients join** using QR code (session ID, IP, port).
3. **Clients submit observations** → master validates and appends to pending records.
4. **Master updates UI** immediately (same as local entry).
5. **Master uploads** to server as today (single source of truth).
6. **End telling** → master finalizes envelope and uploads full counts.

### Consistency and conflict rules
- Master assigns authoritative `idLocal` and `groupid`.
- Clients may re-send if no ack; master deduplicates by `clientEventId`.
- Master only: uploads and `onlineid` ownership.

## UX design (future)
- **Master start screen:** “Start telling as master” toggle.
- **Client start screen:** “Join telling” with QR scan and fallback to manual IP.
- **Status badges:** “Connected to master,” “Pending local submissions,” “Offline.”

## Permissions and Android constraints
- **Wi-Fi LAN:** `INTERNET` only (already in manifest).
- **Wi-Fi Direct:** additional permissions (depends on API level; may require location).
- **Bluetooth:** `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` already in manifest.
  - This aligns with pairing a Bluetooth HID.
  - Codebase does not yet use Bluetooth APIs; permission UI can mention this for future.
- **Foreground service:** likely needed if the master must remain discoverable when screen off.

## Risks and mitigations
- **Battery usage:** keep master in foreground; reduce network chatter.
- **Discovery failures:** QR code + manual IP fallback.
- **Data loss:** client queues unsent events; master persists envelope after each event.
- **Security:** session token + short pairing code; optional TLS.

## Suggested phased roadmap
1. **Phase 1 (LAN only):**
   - Master WebSocket server + client sender.
   - QR pairing + basic ack.
2. **Phase 2 (offline resilience):**
   - Client queue + retry.
   - Master deduplication.
3. **Phase 3 (fallback transport):**
   - Wi-Fi Direct or Bluetooth Classic.
4. **Phase 4 (enhanced UX):**
   - Live totals on clients, reconnection flows.

## Addendum: guaranteed Wi-Fi hotspot + best-practice rollout
- **Assumption:** there is always a Wi-Fi hotspot available (master device or dedicated hotspot). This makes a LAN-first design the most robust and scalable.
- **Best-practice (current):**
  - **Transport:** WebSocket (or gRPC) over local LAN with mDNS/NSD for discovery.
  - **Pairing:** QR code with session token + short PIN (timeboxed, e.g., 2–5 minutes).
  - **Security:** HMAC-signed events minimum; optional TLS if manageable.
  - **Reliability:** client retry queue + master dedup on `clientEventId`.
  - **UX:** master shows connected clients + status; clients show connected/offline/pending.
  - **Performance:** micro-batch events every 250–500 ms and throttle broadcasts to save battery.

## Addendum: late corrections after initial observation
- **Recommended model:** immutable observation events + correction (amend) events.
- **Mechanism:** clients send a correction event referencing `targetEventId` with delta or new value.
- **Master behavior:** update local totals immediately, keep both events for audit.
- **Server upload:** master uses corrected totals on final upload; if server supports updates, send correction events as updates.

## Addendum: solo-mode compatibility
- **Default mode:** remains the current solo flow (no network), fully intact.
- **Opt-in master mode:** only enabled when user explicitly starts master session.
- **No clients present:** app behaves exactly as today; master mode does not alter local workflow.

## Addendum: ad-hoc client onboarding during an active telling
- **Yes, feasible:** provide an “Add clients” button in `TellingScherm` that starts discovery/pairing.
- **Flow:** master opens a pairing window (QR/PIN), clients join one-by-one without interrupting the counting UI.
- **Safety:** joining does not reset or overwrite current records; new clients start sending events immediately after pairing.

## Addendum: visual flow (ASCII)

### Master-client event flow (LAN)
```
[Client A]    [Client B]      [Client N]
    |             |                |
    |  event      |   event        |   event
    |------------>|                |
    |             |------------>   |
    |             |                |---------->
    |                             [MASTER]
    |<------------------------------|  ACK
    |             |<----------------|  ACK
    |<------------------------------|  ACK
    |
    |   (optional periodic resync every 15 min)
    v
[Client queues drained after ACK]
```

### Ad-hoc client onboarding during active telling
```
[MASTER TellingScherm]
        |
        |  "Add clients" button
        v
[Pairing window opens: QR + PIN]
        |
        |  Client scans QR / enters PIN
        v
[Client connected]
        |
        |  Client starts sending events
        v
[MASTER updates totals + logs]
```

## Addendum: master hotspot + mobile data uplink behavior
- **Typical Android behavior:** when the master phone enables a Wi‑Fi hotspot, it keeps its own internet via **mobile data** while sharing that data to clients.
- **Master weather/server access:** the master continues to fetch weather and upload to the server via mobile data.
- **Local LAN impact:** clients connect to the master hotspot and send events locally; this is independent of server connectivity.
- **If mobile data is weak/offline:** LAN still works; master queues uploads until internet returns.
- **Device variability:** some phones disable Wi‑Fi when hotspot is on; this usually does not affect mobile‑data uplink.

# Nederlandstalige audit (samenvatting en uitwerking)

### Doel
Een master-client model op de telpost waarbij **slechts één toestel** (master) de telling start en uploadt, terwijl andere toestellen (clients) lokaal waarnemingen kunnen doorgeven die live in het master-scherm verschijnen.

### Kernconclusie
Dit is **technisch haalbaar** in Android/Kotlin. De veiligste en meest robuuste aanpak is een **lokale Wi-Fi LAN master** (tablet/telefoon) met een kleine server (bijv. WebSocket). Clients sturen enkel “events” (waarnemingen) door; de master blijft de enige bron die naar de server uploadt.

### Huidige situatie (codebase)
- **Uploads:** gebeuren via `DataUploader` (per waarneming) en `AfrondWorker` (eind-enveloppe).
- **Enveloppe en records:** `ServerTellingEnvelope` + `ServerTellingDataItem` zijn al aanwezig en bruikbaar als basis voor event-berichten.
- **Herstelmogelijkheden:** `RecordsBeheer` bewaart een lokale index en back-ups.
- **Serverbeperking:** één actieve telling tegelijk → master moet eigenaar blijven.

### Aanbevolen communicatie-opties
1. **Wi-Fi LAN (zelfde netwerk of master-hotspot)**
   - **Voordelen:** snel, stabiel, geschikt voor meerdere clients.
   - **Nadeel:** vereist hetzelfde netwerk.
   - **Aanbevolen als primaire aanpak.**

2. **Wi-Fi Direct (fallback)**
   - **Voordeel:** geen router nodig.
   - **Nadeel:** complexere UX, wisselende group owner.

3. **Bluetooth Classic (fallback)**
   - **Voordeel:** werkt zonder Wi-Fi, ok voor 1–2 clients.
   - **Nadeel:** lagere snelheid, lastiger multi-client beheer.

4. **BLE**
   - **Niet ideaal** voor streaming/logs of meerdere clients.

5. **Nearby Connections (Google Play Services)**
   - **Voordeel:** discovery + transportkeuze uit handen.
   - **Nadeel:** afhankelijk van Google Play Services.

### Voorgestelde architectuur
**Master toestel**
- Start telling → krijgt `onlineid`.
- Host lokale server (WebSocket of gRPC).
- Verwerkt inkomende client-events en maakt daar `ServerTellingDataItem` van.
- Uploadt naar de server (zoals nu), enkel vanuit master.

**Client toestellen**
- Koppelen met master (QR-code of IP:poort).
- Sturen event-berichten (soort, aantal, aantal_terug, tijdstip, annotaties).
- Houden een lokale retry-queue aan bij tijdelijke disconnectie.

### Event-protocol (voorstel)
**Client → Master**
- `clientEventId` (uniek per client)
- `soortid`
- `aantal`, `aantalterug`
- `tijdstip`
- optioneel: annotaties (leeftijd, geslacht, kleed, opmerking)

**Master → Client**
- `ack` + toegewezen `idLocal`
- actuele totalen
- eventuele foutmelding

**Master → alle clients**
- live updates van totalen en logs (optioneel)

### Dataflow (toekomstbeeld)
1. Master start telling → `onlineid` wordt opgehaald.
2. Clients verbinden via QR-code of IP.
3. Clients sturen events → master verwerkt en toont ze live.
4. Master uploadt naar server, één centrale bron.
5. Bij afronden: master stuurt volledige enveloppe.

### Conflictoplossing
- Master is **altijd** authoritative.
- Clients krijgen ACK met definitive IDs.
- Deduplicatie via `clientEventId`.

### UX-ideeën
- **Master-modus:** “Start telling als master”
- **Client-modus:** “Sluit aan op telling” + QR-scan
- Statusbalk: “Verbonden / Offline / Pending uploads”

### Permissions en beperkingen
- **Wi-Fi LAN:** enkel `INTERNET` (reeds aanwezig).
- **Wi-Fi Direct:** mogelijk extra locatierechten.
- **Bluetooth:** permissies staan al in manifest (OK voor HID/latere fallback).
- **Foreground Service:** nodig als master discoverable moet blijven wanneer scherm uit staat.

### Risico’s + mitigaties
- **Batterij:** beperk broadcast-frequentie, houd master in foreground.
- **Discovery issues:** QR-code fallback + manuele IP.
- **Dataverlies:** client-queue + master-persist na elk event.
- **Security:** pairing-token + korte pincode.

### Gefaseerde roadmap (voorstel)
1. **Phase 1:** LAN-only (WebSocket server + clients).
2. **Phase 2:** retry-queue + deduplicatie.
3. **Phase 3:** Wi-Fi Direct of Bluetooth fallback.
4. **Phase 4:** UX verfijning + live totalen op clients.

### Aanvulling: gegarandeerde Wi-Fi hotspot + best-practice keuzes
- **Uitgangspunt:** er is altijd een Wi-Fi hotspot aanwezig (master toestel of dedicated hotspot). Dit maakt een LAN-oplossing de meest robuuste en schaalbare keuze.
- **Best-practice (2026):**
  - **Transport:** WebSocket (of gRPC) over lokaal LAN met mDNS/NSD voor discovery.
  - **Pairing:** QR-code met sessie-token + korte pincode (timeboxed, bijv. 2–5 minuten geldig).
  - **Security:** minimaal HMAC-signed events; optioneel TLS (self-signed of local CA) indien beheerbaar.
  - **Reliability:** client-side retry queue + master-side deduplicatie op `clientEventId`.
  - **UX:** master toont actieve clients + status; clients tonen “verbonden/offline/pending”.
  - **Performance:** batch events per 250–500 ms en throttle updates naar clients om batterij te sparen.

### Aanvulling: late correcties na initiële waarneming
- **Aanbevolen model:** onveranderlijke waarnemings-events + correctie (amend) events.
- **Mechanisme:** clients sturen een correctie-event met verwijzing naar `targetEventId` met delta of nieuwe waarde.
- **Master gedrag:** werk lokale totalen onmiddellijk bij, houd beide events voor audit.
- **Server upload:** master gebruikt gecorrigeerde totalen bij finale upload; als server updates ondersteunt, stuur correctie-events als updates.

### Aanvulling: solo-mode compatibiliteit
- **Standaardmodus:** blijft de huidige solo-flow (geen netwerk), volledig intact.
- **Opt-in mastermodus:** alleen ingeschakeld wanneer gebruiker expliciet een master-sessie start.
- **Geen clients aanwezig:** app gedraagt zich precies zoals vandaag; mastermodus verandert de lokale workflow niet.

### Aanvulling: ad-hoc client onboarding tijdens een actieve telling
- **Ja, haalbaar:** bied een “Voeg clients toe” knop in `TellingScherm` die discovery/pairing start.
- **Flow:** master opent een koppelvenster (QR/PIN), clients voegen zich een voor een toe zonder de tel-UI te onderbreken.
- **Veiligheid:** aansluiten reset of overschrijft huidige records niet; nieuwe clients beginnen onmiddellijk met het verzenden van events na het koppelen.

### Grafische flow (ASCII)

**Master-client eventflow (LAN)**
```
[Client A]    [Client B]      [Client N]
    |             |                |
    |  event      |   event        |   event
    |------------>|                |
    |             |------------>   |
    |             |                |---------->
    |                             [MASTER]
    |<------------------------------|  ACK
    |             |<----------------|  ACK
    |<------------------------------|  ACK
    |
    |   (optionele resync elke 15 min)
    v
[Client queues leeg na ACK]
```

**Ad-hoc clients toevoegen tijdens actieve telling**
```
[MASTER TellingScherm]
        |
        |  "Voeg clients toe" knop
        v
[Pairing venster: QR + PIN]
        |
        |  Client scant QR / geeft PIN in
        v
[Client verbonden]
        |
        |  Client stuurt events
        v
[MASTER werkt totalen + logs bij]
```

**Master-client hotspot gedrag**
```
[MASTER met hotspot aan]
        |
        |  Clients verbinden via hotspot
        v
[Lokale LAN-communicatie]
        |
        |  Clients sturen events
        v
[MASTER verwerkt events]
        |
        |  (blijft internettoegang houden via mobiele data)
        v
[Uploads en weergegevens ophalen]
```

**Bij slechte mobiele data of offline**
```
[MASTER met hotspot aan]
        |
        |  Clients verbinden via hotspot
        v
[Lokale LAN-communicatie]
        |
        |  Clients sturen events
        v
[MASTER verwerkt events]
        |
        |  (uploads worden in de wachtrij gezet)
        v
[Wacht op herstelde internetverbinding]
        |
        |  (bij herstel: uploads verzenden)
        v
[MASTER uploadt verzamelde gegevens]
```

