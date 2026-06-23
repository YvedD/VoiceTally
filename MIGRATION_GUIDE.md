# Migratie Gids: Volledige Refactoring naar Hilt + MVVM

## 📋 Uitvoerende Samenvatting

Een volledige refactoring-audit van VoiceTally is succesvol voltooid om het volgende te adopteren:
- **Hilt** voor dependency injectie
- **MVVM** met LiveData/StateFlow
- **Room** voor lokale persistentie
- **Repository pattern** voor data toegang
- **Controllers** voor feature logica
- **Multi-module architectuur** met feature modules

## 🔄 Belangrijkste Wijzigingen

### 1. Dependency Injection (Hilt)

#### Voor:
```kotlin
// Handmatige instantiatie
val repository = TellingRepository(context)
val viewModel = TellingViewModel(application, repository)
```

#### Na:
```kotlin
@HiltAndroidApp
class VT5App : Application() { ... }

class TellingScherm : AppCompatActivity() {
    @AndroidEntryPoint
    private val viewModel: TellingViewModel by viewModels()
}

@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val repository: TellingRepository,
    private val speechController: SpeechInputController,
    ...
) : AndroidViewModel(application)
```

### 2. Data Persistentie

#### Voor:
- Handmatige SharedPreferences
- Geen DB structuur

#### Na:
```
VT5Database (Room)
├── TellingEntity (telsessies)
└── ObservationEntity (individuele observaties)

DataStore (type-veilige voorkeuren)
├── Opgeslagen Envelopes
└── Gebruikersvoorkeuren
```

### 3. State Management

#### Voor:
```kotlin
private val _tiles = MutableLiveData<List<SoortRow>>()
val tiles: LiveData<List<SoortRow>> = _tiles
```

#### Na:
```kotlin
private val _tiles = MutableStateFlow<List<SoortRow>>(emptyList())
val tiles: StateFlow<List<SoortRow>> = _tiles.asStateFlow()

// Observeren met repeatOnLifecycle:
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.tiles.collect { tiles ->
            uiManager.updateTiles(tiles)
        }
    }
}
```

### 4. Feature-Specifieke Controllers

Nieuwe architectuur om concerns te scheiden:

```
┌─ SpeechInputController
│  ├─ initialize()
│  ├─ speechEvents: SharedFlow
│  └─ cleanup()
│
├─ BirdNetController
│  ├─ startTicker(config)
│  ├─ events: SharedFlow
│  └─ stopTicker()
│
└─ UploadController
   ├─ upload(envelope, credentials)
   ├─ uploadState: StateFlow
   └─ resetState()
```

## 📦 Nieuwe Aangemaakte Bestanden

### Kern Infrastructuur
```
app/src/main/java/com/yvesds/vt5/
├── di/
│   ├── NetworkModule.kt          - OkHttp + Logging setup
│   ├── StorageModule.kt          - Room Database + DAOs
│   └── ControllersModule.kt      - Controllers provisioning
│
├── core/database/
│   ├── VT5Database.kt            - Room database klasse
│   ├── entity/
│   │   └── TellingEntity.kt       - Entiteiten (Telling, Observation)
│   └── dao/
│       └── TellingDao.kt          - Database toegangsobject
```

### Features
```
app/src/main/java/com/yvesds/vt5/features/telling/
├── TellingViewModel.kt           - MVVM state & logica
├── data/
│   └── TellingRepository.kt       - Repository pattern
├── controller/
│   ├── SpeechInputController.kt
│   ├── BirdNetController.kt
│   └── UploadController.kt
```

### Feature Module
```
feature/telling/
├── build.gradle.kts              - Library module setup
├── src/main/java/.../feature/telling/
│   └── di/TellingFeatureModule.kt    - Feature configuratie
├── consumer-rules.pro            - ProGuard regels voor consumers
└── README.md                      - Feature documentatie
```

### Tests
```
app/src/test/java/com/yvesds/vt5/
├── features/telling/
│   ├── TellingViewModelTest.kt
│   └── controller/ControllerTests.kt

app/src/androidTest/java/com/yvesds/vt5/
└── features/telling/data/
    └── TellingRepositoryTest.kt
```

### Gradle Configuratie
```
gradle/libs.versions.toml         - Gecentraliseerde dependency versies
app/build.gradle.kts              - Bijgewerkt met Hilt, Room, etc.
settings.gradle.kts               - Inclusief :feature:telling module
```

## 🔌 Specifieke Migraties

### TellingScherm (Activity Basis)

#### Setup
```kotlin
@AndroidEntryPoint
class TellingScherm : AppCompatActivity() {
    private val viewModel: TellingViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ...
        setupViewModelObservation()
    }
    
    private fun setupViewModelObservation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observeer en reageer op state wijzigingen
                launch { viewModel.tiles.collect { ... } }
                launch { viewModel.finals.collect { ... } }
                launch { viewModel.error.collect { handleError(it) } }
            }
        }
    }
}
```

### TellingViewModel (ViewModel + Controllers)

#### Lifecycle
```kotlin
@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val repository: TellingRepository,
    private val speechController: SpeechInputController,
    private val birdNetController: BirdNetController,
    private val uploadController: UploadController
) : AndroidViewModel(application) {
    
    init {
        initializeControllers()
        loadPendingRecords()
    }
    
    override fun onCleared() {
        stopSpeechInput()
        stopBirdNetTicker()
    }
}
```

#### State Management
```kotlin
// Inputs (mutable)
private val _tiles = MutableStateFlow<List<SoortRow>>(emptyList())
private val _error = MutableStateFlow<AppError?>(null)

// Outputs (immutable)
val tiles: StateFlow<List<SoortRow>> = _tiles.asStateFlow()
val error: StateFlow<AppError?> = _error.asStateFlow()

// State modifiers
fun setTiles(list: List<SoortRow>) { _tiles.value = list }
fun clearError() { _error.value = null }
```

## 🧪 Test Strategie

### Unit Tests (JVM)
```bash
./gradlew test
```
- TellingViewModelTest: State management, foutafhandeling
- ControllerTests: Event creatie, state transities
- Repository logica zonder Hilt

### Instrumented Tests (Android Apparaat)
```bash
./gradlew connectedAndroidTest
```
- TellingRepositoryTest: Room CRUD operaties
- Integratie met database
- Hilt component testing

## 🚀 Volgende Stappen

1. **Compileren en Testen**:
   ```bash
   ./gradlew clean build --scan
   ./gradlew test && ./gradlew connectedAndroidTest
   ```

2. **Waarschuwingen Oplossen**:
   - Controleer ontbrekende imports (voeg tijdelijk bestand toe indien nodig)
   - Verifieer Hilt setup in AndroidManifest.xml

3. **Runtime Valideren**:
   - Voer app uit op emulator
   - Test volledige tel flow
   - Verifieer persistentie met SQLite

4. **Multi-Module Uitbreiden**:
   - Maak `:feature:speech` voor SpeechHandler
   - Maak `:feature:birdnet` voor BirdNet integratie
   - Maak `:core:database` voor database + entiteiten

5. **Documentatie**:
   - Maak architecture decision records (ADRs)
   - Documenteer feature flags
   - Setup van CI/CD met automatische tests

## 📚 Referenties

- [Hilt Officiële Documentatie](https://dagger.dev/hilt)
- [Room Database Gids](https://developer.android.com/training/data-storage/room)
- [MVVM Architectuur](https://developer.android.com/jetpack/guide/architecture)
- [StateFlow vs LiveData](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)

## ⚠️ Belangrijke Overwegingen

1. **Backward Compatibiliteit**: 
   - De klasse `TellingLogManager` blijft behouden voor compatibiliteit
   - Bestaande helpers blijven werken zonder wijzigingen

2. **Migratiepad**:
   - Verwijder oude methodes pas nadat nieuwe werken
   - Gebruik feature flags voor geleidelijke uitrol indien nodig

3. **Prestaties**:
   - Room queries met Flow zijn lazy (blokkeren UI niet)
   - StateFlow is efficiënter dan LiveData voor meerdere observers
   - Hilt injectie is compile-time (geen reflectie in runtime)

4. **Debugging**:
   - Hilt genereert code in `build/generated/hilt_curated_src`
   - Database inspector in Android Studio (Built-in Database Inspector)
   - Flow debugging met breakpoints in `.collect { }`

---

**Versie-upgrade: Hilt 2.51.1 → 2.59.2**, **AndroidX Hilt Navigation 1.2.0 → 1.3.0**
**Refactoring Voltooid: 2026-06-15**
**Status: ✅ Klaar voor compilatie en testing**
