# 📋 Refactoring Plan: TellingScherm — Elimineer de God-Class

## 🔍 Huidige Situatie (Probleemanalyse)

`TellingScherm.kt` is een **God Class** van ~2000+ regels die verantwoordelijk is voor:

| Verantwoordelijkheid | Huidige locatie | ~Regels |
|---|---|---|
| UI lifecycle (onCreate, onResume, onPause, onDestroy) | In `TellingScherm` zelf | ~150 |
| Spraakherkenning (hypotheses, parsering, resultaten) | Inline callbacks & methods | ~300 |
| Master-Client netwerk (QR, pairing, server, connector) | Inline logic & 30+ methods | ~600 |
| BirdNET SSE ticker (detecties, ontdekking, pingen) | Inline logic & 10+ methods | ~150 |
| Upload/Afronden (envelope bouwen, versturen) | Inline + delegatie naar `TellingAfrondHandler` | ~150 |
| Tile management (aggregatie, sync, update) | Inline + delegatie naar `TegelBeheer` | ~200 |
| Backup & persistentie (SAF, envelope opslag) | Inline + delegatie | ~100 |
| Annotation & species managers | Inline callbacks | ~100 |
| Dialogen (confirm, suggestie, QR, etc.) | Inline | ~100 |
| Diverse helper callbacks & state | Doorheen de klasse | ~150 |

**Kernprobleem:** `TellingScherm` kent en beheert alle domeinlogica zelf, in plaats van deze te delegeren aan gespecialiseerde controllers/services die door Hilt worden geïnjecteerd.

---

## 🎯 Doelstelling

Verwijder alle domeinlogica uit `TellingScherm` en verplaats deze naar bestaande of nieuwe **controllers** die via Hilt worden geïnjecteerd. `TellingScherm` wordt een **dunne UI-laag** die enkel:
1. Lifecycle events afhandelt
2. ViewModel state observeert
3. Gebruikersinteracties doorgeeft aan controllers
4. UI updates toepast via `TellingUiManager`

---

## 📦 Fase 1: Master-Client Controller (Grootste blok: ~600 regels)

### Te extraheren functionaliteit
- `mcPairingManager`, `mcEventProcessor`, `mcMasterServer`, `mcEventQueue`, `mcClientConnector` velden
- Alle master-client state (`clientModeLocked`, `pendingClientConnectionPayload`, etc.)
- QR-code scannen en coderen
- Pairing flow (`beginClientPairingFlow`, `prepareMasterMode`, `connectClientToMaster`)
- Server starten/stoppen (`startMasterServerOnDemand`)
- Client connectie & event queue
- Tile sync (broadcast & receive)
- Client events verwerken (`handleLiveClientEvent`, `applyClientRecordUpdate`)
- Alle `bindMasterRuntimeCallbacks`, `bindClientRuntimeCallbacks`
- Dialogen voor master-client (`showMasterPairingDialog`, `requestClientApproval`)

### Te maken bestand
```
app/src/main/java/com/yvesds/vt5/features/telling/controller/MasterClientController.kt
```

### Interface naar TellingScherm
```kotlin
class MasterClientController @Inject constructor(
    private val application: Application,
    private val scope: CoroutineScope, // of lifecycleScope via injectie
    private val tegelBeheer: TegelBeheer,
    private val speciesManager: TellingSpeciesManager,
    private val logManager: TellingLogManager
) {
    // Events die de Activity kan observeren
    val connectionState: StateFlow<McConnectionState>
    val clientEvents: SharedFlow<McClientEvent>
    
    fun startMaster()
    fun startClient(payload: McQrPayload)
    fun stopClient()
    fun handleQrScanResult(raw: String): Boolean
    fun buildTileSyncItems(): List<TileSyncItem>
    fun applyTileSync(items: List<TileSyncItem>)
    fun queueObservation(record: ServerTellingDataItem)
    // ...
}
```

### Wijzigingen in TellingScherm
```kotlin
@AndroidEntryPoint
class TellingScherm : AppCompatActivity() {
    @Inject lateinit var mcController: MasterClientController
    
    // Verwijder: alle mc-velden (~20 stuks)
    // Verwijder: ~30 methods (bind*, handle*, start*, etc.)
    // Vervang door: mcController.startMaster(), mcController.handleQrScanResult(), etc.
}
```

---

## 📦 Fase 2: BirdNET Controller (gebruik bestaand, ~150 regels)

Bestaand `BirdNetController.kt` is **te dun**. Het omvat alleen de SSE ticker, niet de UI-interactie (ontdekking, pingen, dialogen).

### Uitbreiden met
- `discoverHost()` — verplaats `discoverBirdNetHost()` uit TellingScherm
- `checkHost()` — verplaats `checkCurrentBirdNetHost()` uit TellingScherm
- `handleBirdNetPressed()` — dialog logica verplaatsen
- `formatPendingTickerText()` — formattering verplaatsen

### Interface
```kotlin
class BirdNetController @Inject constructor(
    private val application: Application,
    private val scope: CoroutineScope
) {
    val tickerText: StateFlow<String>
    val connectionState: StateFlow<BirdNetConnectionState>
    
    fun startTicker()
    fun stopTicker()
    suspend fun discoverHost(): BirdNetDiscoveryResult?
    suspend fun checkHost(): Boolean
    fun showConfigDialog(activity: AppCompatActivity)
    fun clearConfig()
}
```

### Wijzigingen in TellingScherm
```kotlin
// Verwijder: startBirdNetPendingTickerIfNeeded()
// Verwijder: stopBirdNetPendingTicker()
// Verwijder: handleBirdNetPressed()
// Verwijder: discoverBirdNetHost()
// Verwijder: checkCurrentBirdNetHost()
// Verwijder: formatBirdNetPendingTickerText()
// Vervang: birdNetController.startTicker() in onResume
// Vervang: birdNetController.stopTicker() in onPause
// Vervang: birdNetController.handleBirdNetPressed() in callback
```

---

## 📦 Fase 3: Upload/Afrond Controller (~150 regels)

Bestaand `UploadController.kt` is te beperkt (enkel API upload). Uitbreiden met de volledige afrond-flow.

### Uitbreiden met
- `handleAfronden()` — verplaats uit TellingScherm
- `showVervolgtellingDialog()` — dialog logica
- `showAutoDismissSuccess()` — UI helper
- Envelope bouwen met metadata

### Interface
```kotlin
class UploadController @Inject constructor(
    private val application: Application,
    private val scope: CoroutineScope,
    private val afrondHandler: TellingAfrondHandler,
    private val envelopePersistence: TellingEnvelopePersistence
) {
    suspend fun finalizeAndUpload(
        pendingRecords: List<ServerTellingDataItem>,
        metadataUpdates: MetadataUpdates?
    ): AfrondResult
    
    fun showVervolgtellingDialog(activity: AppCompatActivity, eindtijd: String)
}
```

---

## 📦 Fase 4: Speech Controller (gebruik bestaand, ~300 regels)

Bestaand `SpeechInputController.kt` heeft callbacks die nog naar TellingScherm verwijzen. De **hypothese-verwerkingslogica** (`handleSpeechHypotheses`) zit nog in TellingScherm.

### Uitbreiden met
- `handleHypotheses()` — verplaats parse-logica uit TellingScherm
- `recognizeCandidate()` — verplaats `handleRecognizedCandidate()`
- `autoAddRecognizedSpecies()` — verplaats
- `showAddSpeciesConfirmationDialog()` — dialog logica

### Interface
```kotlin
class SpeechInputController @Inject constructor(
    private val scope: CoroutineScope,
    private val speechHandler: TellingSpeechHandler,
    private val matchResultHandler: TellingMatchResultHandler,
    private val speciesManager: TellingSpeciesManager,
    private val tegelBeheer: TegelBeheer
) {
    val speechEvents: SharedFlow<SpeechEvent>
    
    fun initialize()
    fun handleHypotheses(utteranceId: String, hypotheses: List<Pair<String, Float>>, partials: List<String>)
    fun recognizeCandidate(utteranceId: String?, candidate: Candidate, count: Int)
    fun cleanup()
}
```

---

## 📦 Fase 5: UI-only methods opschonen

Na extractie van bovenstaande controllers blijven er nog **log-helpers** en **UI-delegaties** over in TellingScherm.

### Te verplaatsen/logisch te groeperen
- Alle `addLog`, `upsertPartialLog`, `addFinalLog`, etc. → **`TellingLogManager`** (al bestaat)
- `handlePartialTap` → **`TellingDialogHelper`** of nieuwe **SuggestionController**
- `handleFinalTap` → **`TellingAnnotationHandler`** (al bestaat)
- `handleTileTapIncrement` → **`TegelBeheer` + `TileTapAggregationManager`** (al bestaat)
- `showNumberInputDialog` → **`TellingDialogHelper`** (al bestaat)
- `showSuggestionBottomSheet` → **Nieuwe `SuggestionController`**
- `handleSaveClose` → **`TellingUiManager`** (al bestaat)
- `refreshDailyTotalsUi`, `syncDailyTotalsRecord` → **`DailyDirectionTotalsStore`** of nieuwe **TotalsController**

---

## 📊 Overzicht Te Verwijderen Uit TellingScherm

| Fase | Aantal methods | ~Regels | Nieuw bestand |
|---|---|---|---|
| Fase 1: Master-Client | ~30 | ~600 | `MasterClientController.kt` |
| Fase 2: BirdNET | ~6 | ~150 | Uitbreiden `BirdNetController.kt` |
| Fase 3: Upload/Afrond | ~5 | ~150 | Uitbreiden `UploadController.kt` |
| Fase 4: Speech | ~8 | ~300 | Uitbreiden `SpeechInputController.kt` |
| Fase 5: UI opschonen | ~15 | ~300 | Bestaande helpers |
| **Totaal** | **~64** | **~1500** | |

**Resultaat:** `TellingScherm.kt` krimpt van ~2000+ regels naar **~500 regels** (enkel lifecycle, ViewModel binding, UI setup).

---

## 🛠️ Concrete Implementatie-Stappen

### Stap 1: MasterClientController.kt aanmaken
```kotlin
// NIEUW BESTAND
package com.yvesds.vt5.features.telling.controller

@Singleton
class MasterClientController @Inject constructor(
    private val application: Application,
    @Named("lifecycleScope") private val scope: CoroutineScope,
    private val tegelBeheer: TegelBeheer,
    private val speciesManager: TellingSpeciesManager
) {
    private val TAG = "MasterClientController"
    
    private var mcPairingManager: PairingManager? = null
    private var mcEventProcessor: MasterEventProcessor? = null
    private var mcMasterServer: MasterServer? = null
    private var mcEventQueue: ClientEventQueue? = null
    private var mcClientConnector: ClientConnector? = null
    
    private val _connectionState = MutableStateFlow(McConnectionState.Disconnected)
    val connectionState: StateFlow<McConnectionState> = _connectionState.asStateFlow()
    
    // ... verplaats ALLE master-client logica hierheen
}
```

### Stap 2: Dependencies registreren in Hilt module
```kotlin
// In ControllersModule.kt toevoegen:
@Provides @Singleton
fun provideMasterClientController(
    application: Application,
    tegelBeheer: TegelBeheer,
    speciesManager: TellingSpeciesManager
): MasterClientController {
    return MasterClientController(application, ..., tegelBeheer, speciesManager)
}
```

### Stap 3: BirdNetController uitbreiden
Voeg methodes toe uit TellingScherm voor discovery, ping, dialogen.

### Stap 4: UploadController uitbreiden
Voeg `finalizeAndUpload()` en dialogen toe.

### Stap 5: SpeechInputController uitbreiden
Voeg hypothese-verwerking en candidate-logica toe.

### Stap 6: TellingScherm opschonen
Verwijder alle inline logica, vervang door controller-aanroepen.

### Stap 7: Testen
```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## ✅ Eindresultaat

Na deze refactoring is `TellingScherm` **geen God Class meer** maar een dunne Activity die:

1. ✅ Alleen UI lifecycle beheert
2. ✅ ViewModel state observeert met `repeatOnLifecycle`
3. ✅ Gebruikersacties doorstuurt naar gespecialiseerde controllers
4. ✅ UI updates toepast via `TellingUiManager`
5. ✅ Geen directe kennis heeft van netwerk, spraak, BirdNET, uploads of databases

**Architectuur:**
```
TellingScherm (Activity) → dunne UI-laag
  ├── TellingViewModel → state management
  ├── MasterClientController → netwerk & pairing
  ├── BirdNetController → BirdNET SSE
  ├── UploadController → afronden & upload
  ├── SpeechInputController → spraakherkenning
  ├── TellingUiManager → UI updates
  └── Overige helpers (TegelBeheer, TellingLogManager, etc.)
```

---

*Gebaseerd op de bestaande migratie naar Hilt + MVVM (zie `MIGRATION_GUIDE.md`)*
