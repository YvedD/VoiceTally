# Task Plan: Offline Data-Sync & P2P Architectuur voor VoiceTally (VT5)

Dit document beschrijft de stapsgewijze taakverdeling voor het implementeren van de robuuste offline P2P synchronisatie-architectuur met QR-code koppeling.

---

## Overzicht van Fases

- [ ] **Fase 1: Project Configuratie, Permissies & Gradle Afhankelijkheden**
- [ ] **Fase 2: Auxiliary Sync Tracking (Room Database Behoud)**
- [ ] **Fase 3: QR-Code Koppelingsmechanisme (CameraX & QR Generator)**
- [ ] **Fase 4: Nearby Connections P2P Manager & Foreground Service**
- [ ] **Fase 5: Synchronisatie Logica, JSON Serialisatie & Reconnection Fallback**
- [ ] **Fase 6: Validatie, Logging & Testdekking**

---

## Gedetailleerde Taakbeschrijving

### Fase 1: Project Configuratie, Permissies & Gradle Afhankelijkheden
- [ ] **1.1** Voeg Google Play Services Nearby (`com.google.android.gms:play-services-nearby`) toe aan `app/build.gradle.kts`.
- [ ] **1.2** Voeg CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`) en ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`) toe voor de QR-scanner.
- [ ] **1.3** Werk `AndroidManifest.xml` bij met benodigde Android 13+ (API 33+) permissies:
  - `NEARBY_WIFI_DEVICES` (met `android:usesPermissionFlags="neverForLocation"`)
  - `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
  - `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
  - `CAMERA`
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` (voor de P2P Foreground Service).
- [ ] **1.4** Implementeer runtime permissie aanvraag logica in de UI (`PermissionsManager`).

### Fase 2: Auxiliary Sync Tracking (Room Database Behoud)
- [ ] **2.1** Verifieer dat de bestaande Room entiteiten en tabellen (vogeltellingen, soorten, protocollen) **100% ongewijzigd** blijven.
- [ ] **2.2** Creëer een *nieuwe, onafhankelijke* Room entiteit `SyncQueueEntity` (of `SyncTrackingTable`) in een apart DAO/Entity pakket om synchronisatiestatus bij te houden (bijv. `id`, `recordId`, `payloadJson`, `synced: Boolean`, `timestamp`).
- [ ] **2.3** Implementeer `SyncQueueDao` om niet-gesynchroniseerde records te beheren en bij te werken na succesvolle P2P overdracht.

### Fase 3: QR-Code Koppelingsmechanisme
- [ ] **3.1** Implementeer een QR-code generator op het Master-apparaat (bijv. via ZXing of ingebouwde bitmap generator) die verbindingsmetadata (Endpoint Name, Service ID, Token) omzet in een scanbare QR-code.
- [ ] **3.2** Implementeer een QR-code scanner scherm voor Teller-apparaten met behulp van CameraX en ML Kit Barcode Scanning.
- [ ] **3.3** Parse de gescande QR-payload om direct de Nearby Connections verbinding met de specifieke Master te initiëren (bypassing openbare discovery).

### Fase 4: Nearby Connections P2P Manager & Foreground Service
- [ ] **4.1** Ontwikkel `P2PConnectionManager` als centrale Singleton / Repository om `ConnectionsClient` aan te sturen (Advertising voor Master, Discovery/Connection voor Tellers).
- [ ] **4.2** Implementeer een `P2PForegroundService` met een persistente Android 13+ notificatie om te voorkomen dat het OS de P2P verbinding beëindigt tijdens langdurige veldobservaties.
- [ ] **4.3** Handel verbindingscallbacks af (`onConnectionInitiated`, `onConnectionResult`, `onDisconnected`).

### Fase 5: Synchronisatie Logica, JSON Serialisatie & Reconnection Fallback
- [ ] **5.1** Definieer een robuust JSON data-protocol voor tellingen (`TallySyncPayload`) met Kotlinx.serialization of Gson.
- [ ] **5.2** Implementeer automatische buffering in de `SyncQueueDao` wanneer een Teller offline raakt of de verbinding wegvalt.
- [ ] **5.3** Implementeer automatische synchronisatie-trigger zodra `onConnectionResult` succesvol hersteld is (`connectionResult.status.isSuccess == true`). De Teller stuurt automatisch alle records waar `synced == false` naar de Master.
- [ ] **5.4** Master ontvangt payloads, valideert deze, slaat ze op in zijn eigen Room database (zonder de bestaande schema's aan te tasten) en stuurt een `ACK` (acknowledgment) terug naar de Teller om `synced = true` te markeren.

### Fase 6: Validatie & Testdekking
- [ ] **6.1** Schrijf unit tests voor `SyncQueueDao` en payload serialisatie.
- [ ] **6.2** Verifieer de werking op testapparaten / emulators (inclusief offline modus en herverbindingssimulatie).
