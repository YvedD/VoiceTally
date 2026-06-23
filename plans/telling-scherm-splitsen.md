# Oplossing: TellingScherm.kt Opsplitsen (God-class Refactor)

**Probleem:** `TellingScherm.kt` is ~3123 regels met ~20 verantwoordelijkheden.

---

## Doelstelling

Verklein `TellingScherm.kt` van ~3123 naar **maximaal 400 regels** door logica te verplaatsen naar gespecialiseerde klassen.

---

## Stappenplan

### Fase 1: Extractie van UI-componenten (Week 1)

| Te extraheren component | Nieuwe klasse | Huidige locatie (approx) |
|-------------------------|---------------|--------------------------|
| Speech log adapter + logica | `SpeechLogController` | regels 150-250 |
| Tegel/tile adapter + logica | `TegelController` | regels 250-400 |
| Annotaties UI | `AnnotatieController` | regels 400-550 |
| Compass UI updates | `CompassController` | regels 550-650 |
| Pending ticker UI | `PendingTickerController` | regels 650-780 |

### Fase 2: Extractie van Bedrijfslogica (Week 2)

| Te extraheren logica | Nieuwe klasse | Beschrijving |
|----------------------|---------------|--------------|
| Speech input lifecycle | `SpeechInputController` (al deels bestaat) | Start/stop speech, event handling |
| BirdNet integratie | `BirdNetController` (al deels bestaat) | SSE client, pending updates |
| Upload logica | `UploadController` (al deels bestaat) | Upload queue, retries |
| Master-client | `MasterClientManager` | TCP server start/stop, client handling |
| Telling lifecycle | `TellingLifecycleManager` | Start/finalize/afronden telling |

### Fase 3: State naar ViewModel (Week 2-3)

**Huidig (in TellingScherm):**
```kotlin
private val pendingRecords = mutableListOf<ServerTellingDataItem>()
private var currentSoorten = listOf<SoortTile>()
private var isTellingGestart = false
private var onlineId: String? = null
private var tellingId: Long = 0L
private var currentTelpostId: String? = null
```

**Doel (in TellingViewModel):**
```kotlin
// StateFlow voor elke state variabele
private val _pendingRecords = MutableStateFlow<List<ServerTellingDataItem>>(emptyList())
private val _currentSoorten = MutableStateFlow<List<SoortTile>>(emptyList())
private val _tellingState = MutableStateFlow<TellingState>(TellingState.Idle)
private val _onlineId = MutableStateFlow<String?>(null)
private val _tellingId = MutableStateFlow(0L)
private val _telpostId = MutableStateFlow<String?>(null)
```

### Fase 4: Resultaat

```
TellingScherm.kt (~300 regels) ← Activity, alleen UI lifecycle
├── onCreate() -> binding + observers
├── setupViewModelObservers()
├── onNewIntent()
├── onActivityResult()
└── UI helper methods

TellingViewModel.kt (~400 regels) ← State management
├── StateFlows voor alle state
├── Functies voor user actions
└── Delegatie naar controllers

SpeechInputController.kt (~200 regels)
BirdNetController.kt (~250 regels)
UploadController.kt (~300 regels)
MasterClientManager.kt (~350 regels)
TellingLifecycleManager.kt (~200 regels)
SpeechLogController.kt (~150 regels)
TegelController.kt (~200 regels)
PendingTickerController.kt (~100 regels)
```

---

## Prioriteit

**🔴 Hoog** — Dit is de grootste bron van technische schuld. Aanpak in 3 sprints van 1 week.

---

## Migratie Risico's

1. **Regressie:** Veel manuele integratietests nodig na elke extractie
2. **Timing:** Speech input en BirdNet zijn real-time; voorzichtig met refactoren
3. **Master-client:** TCP server starten/stoppen is lifecycle-gevoelig
4. **Data consistentie:** `pendingRecords` wordt op meerdere plaatsen gelezen/geschreven

## Aanbevolen Aanpak

1. **Feature flags** gebruiken: nieuwe code naast oude laten bestaan
2. **Per functionaliteit** extracten, niet alles tegelijk
3. **Na elke extractie:** `./gradlew test` draaien + handmatige test op emulator
4. **Eerst de makkelijke** (SpeechLogController, TegelController), dan de complexe (MasterClientManager)
