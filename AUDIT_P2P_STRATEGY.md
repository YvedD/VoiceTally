# P2P Strategie Audit & Definitieve Keuze voor VoiceTally (VT5)

Dit document bevat de definitieve strategische audit waarin alle alternatieve P2P communicatietechnologieën officieel als **Niet-bruikbaar** worden gemarkeerd, en waarin de implementatie en het gebruik van **Google Nearby Connections API** grondig wordt uitgewerkt voor VoiceTally.

---

## 1. Status van Alternatieve Technologieën (Markering: NIET-BRUIKBAAR)

Na grondige evaluatie van de eisen (100% offline open-veldbereik, lage latentie, robuuste herverbinding en Android 13+ compatibiliteit) zijn de volgende technologieën formeel **afgewezen en niet-bruikbaar** verklaard:

1. **Bluetooth Classic (RFCOMM) — ❌ NIET-BRUIKBAAR**
   - *Reden van afwijzing:* Vereist trage, omslachtige handmatige OS-pairing dialogen; instabiel in open veld bij geringe storingsbronnen; ongeschikt voor snelle bulk-dataoverdracht.
2. **Bluetooth Low Energy (BLE / GATT) — ❌ NIET-BRUIKBAAR**
   - *Reden van afwijzing:* Zeer beperkte bandbreedte (MTU limieten) waardoor het verzenden van teltabellen en JSON payloads extreem traag is; ongeschikt voor real-time synchronisatie van intensieve veltellingen.
3. **Wi-Fi Direct (Wi-Fi P2P) — ❌ NIET-BRUIKBAAR**
   - *Reden van afwijzing:* Instabiele groepsonderhandeling (`Group Owner`) in de Android API; verbreekt frequent bij schermvergrendeling of achtergrondstatus; vereist handmatige acceptatie door de gebruiker, wat niet gebruiksvriendelijk is in het veld.

---

## 2. Definitieve Keuze: Google Nearby Connections API

Voor VoiceTally is **Google Nearby Connections API** (gebruikmakend van `Strategy.P2P_STAR`) de enige geaccepteerde en gebruikte technologie.

### A. Kernmechanisme in Nearby Connections
- **Discovery & Advertising:** De Master fungeert als Advertiser (zendt een unieke service ID en payload uit gekoppeld aan de QR-code). De Teller fungeert als Discoverer (zoekt naar de Master op basis van de gescande QR-metadata).
- **Hybrid Transport:** Nearby Connections kiest onder water dynamisch de meest optimale radio (BLE voor energiezuinige discovery en Wi-Fi / Wi-Fi Direct voor snelle, stabiele socket-transmissie).
- **Payload Overdracht:** Data wordt verzonden via `Payload.fromBytes()` of `Payload.fromStream()`, waarbij JSON-gecodeerde `ObservationEvent` berichten worden uitgewisseld conform het reeds aanwezige `McProtocol`.

### B. Implementatievereisten & Workflow
1. **Permissies (Android 13+ / API 33+):**
   - `NEARBY_WIFI_DEVICES` (met `neverForLocation`).
   - Bluetooth permissies (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`).
   - Camera permissie (`CAMERA`) voor de QR-scanner.
2. **Foreground Service:**
   - Een permanente `P2PForegroundService` met `FOREGROUND_SERVICE_CONNECTED_DEVICE` waarborgt dat de verbinding in het veld niet door de OS-batterijoptimalisatie wordt verbroken.
3. **Idempotentie & Statusbeheer:**
   - Elk observatie-evenement krijgt een unieke `clientEventId`. De lokale `SyncLog` buffer zorgt ervoor dat niet-verzonden berichten bewaard blijven en automatisch worden gesynchroniseerd zodra de verbinding (`onConnectionResult`) herstelt. Spraakinvoer op individuele toestellen (eventueel via internet) staat hier volledig los van.
