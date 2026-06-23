# VoiceTally (VT5) — Grondige Moderniserings-Audit
**Datum:** Juni 2026  
**Status:** Comprehensive Refactoring Roadmap  
**Projectschaal:** 134 Kotlin-bestanden, ~3100 LOC in TellingScherm.kt

---

## Executive Summary

VoiceTally is een mid-sized Android-app voor vogeltellingen met complexe spraakherkenning, master-client samenwerking, en cloudupload. De codebase toont organische groei met sterke functionele features, maar suffers van:

- **Godclass anti-pattern** (TellingScherm: infocat-complex)
- **Verspreid state management** (Activity + SharedPreferences + SAF-bestanden)
- **Geen dependency injection** framework
- **Verouderde opslag** (SharedPreferences i.p.v. DataStore)
- **Beperkte error handling** en retry logica
- **Lage testbaarheid** (gedeactiveerde tests, tight coupling)

Dit document beschrijft ALLE problemen en concrete, moderne oplossingen per categorie.

---

## 📋 Audit-Categorieën

### 1. **Architectuur & Design Patterns**
### 2. **State Management**
### 3. **Persistent Storage**
### 4. **Network & API Communicatie**
### 5. **Asynchroon & Coroutines**
### 6. **Testing & Testability**
### 7. **Code Quality & Maintainability**
### 8. **Android Modern Best Practices**
### 9. **Security**
### 10. **Performance & Resource Management**
### 11. **Logging & Monitoring**
### 12. **Documentation & Developer Experience**

---

## 1. 🏗️ Architectuur & Design Patterns

### 1.1 **Godclass Anti-Pattern: TellingScherm.kt**

#### 🔴 PROBLEEM
- **3103+ regels** in één `AppCompatActivity`
- **30+ lateinit var** (helpers, managers, adapters)
- **Verantwoordelijkheden:** UI, spraakherkenning, upload, annotaties, BirdNET, master-client, logging, tiles-beheer, diagnostiek
- **Geen duidelijke separation of concerns**

**Impact:** 
- Moeilijk te begrijpen, onderhouden, testen
- Hoge bug-introductie-risk bij wijzigingen
- Coupled aan Android lifecycle

#### ✅ MODERNA OPLOSSING

**Strategie: Gefaseerde refactoring via Controllers + ViewModel**

```kotlin
// Fase 1: ViewModel als state container (gepartieel ingevoerd)
// Fase 2: Feature-specifieke Controllers
// Fase 3: Event-bus / StateFlow observe-pattern
```

**Stap 1: Creëer Feature Controllers**

```kotlin
// DelimitSpeech-controller
class SpeechInputController(
    private val scope: CoroutineScope,
    private val speechHandler: TellingSpeechHandler,
    private val matchResultHandler: TellingMatchResultHandler
) {
    val speechEvents = MutableSharedFlow<SpeechEvent>()
    
    fun initialize() { /* setup */ }
    fun handleHypotheses(hyp: List<Pair<String,Float>>) { /* delegate */ }
    fun cleanup() { /* teardown */ }
}

// BirdNET-controller
class BirdNetController(
    private val scope: CoroutineScope,
    private val sseClient: BirdNetSseClient
) {
    val detectionUpdates = MutableSharedFlow<BirdNetDetection>()
    
    fun startTicker() { /* manage SSE */ }
    fun stopTicker() { /* cleanup */ }
}

// Upload-controller
class UploadController(
    private val scope: CoroutineScope,
    private val api: TrektellenApi,
    private val workManager: WorkManager
) {
    val uploadProgress = MutableStateFlow<UploadState>(UploadState.Idle)
    
    suspend fun uploadFinalized(envelope: ServerTellingEnvelope): Result<String>
    suspend fun retryFailed()
}
```

**Stap 2: Injecteer Controllers in ViewModel**

```kotlin
class TellingViewModel(application: Application) : AndroidViewModel(application) {
    private val speechController = SpeechInputController(...)
    private val birdNetController = BirdNetController(...)
    private val uploadController = UploadController(...)
    
    val speechEvents: Flow<SpeechEvent> = speechController.speechEvents
    val detections: Flow<BirdNetDetection> = birdNetController.detectionUpdates
    val uploadState: StateFlow<UploadState> = uploadController.uploadProgress
    
    fun initialize() {
        speechController.initialize()
        birdNetController.startTicker()
    }
}
```

**Stap 3: TellingScherm observeert enkel StateFlows**

```kotlin
class TellingScherm : AppCompatActivity() {
    private lateinit var viewModel: TellingViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(TellingViewModel::class.java)
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observeer niet-imperatieven: enkel UI updates
                launch {
                    viewModel.speechEvents.collect { updateUI(it) }
                }
                launch {
                    viewModel.detections.collect { updateUI(it) }
                }
                launch {
                    viewModel.uploadState.collect { updateUI(it) }
                }
            }
        }
    }
}
```

**Voordelen:**
- ✅ TellingScherm ~200-300 LOC (i.p.v. 3100)
- ✅ Controllers zijn unit-testable
- ✅ Logica losgekoppeld van Activity lifecycle
- ✅ Reusable in andere contexten (fragments, compose)

---

### 1.2 **Lack of Dependency Injection**

#### 🔴 PROBLEEM
- **Manual object creation** in `initializeHelpers()`
- **No factory pattern**, **no service locator**
- **Hard-coded dependencies** via constructors
- **Impossible to mock** in tests

```kotlin
// Huidig: alles handmatig
private fun initializeHelpers() {
    logManager = TellingLogManager(MAX_LOG_ROWS)
    dialogHelper = TellingDialogHelper(this, this, safHelper)
    dataProcessor = TellingDataProcessor()
    // ... + 20 meer!
}
```

#### ✅ MODERNA OPLOSSING: Hilt Dependency Injection

**Installatie:**
```bash
# gradle/libs.versions.toml
hilt = "2.59.2"

# app/build.gradle.kts
plugins {
    alias(libs.plugins.hilt.android) apply false
}
dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
```

**Implementatie:**

```kotlin
// 1. Maak Hilt module
@Module
@InstallIn(SingletonComponent::class)
object TellingModule {
    @Singleton
    @Provides
    fun provideLogManager(): TellingLogManager = TellingLogManager(600)
    
    @Singleton
    @Provides
    fun provideSaFStorageHelper(
        @ApplicationContext context: Context
    ): SaFStorageHelper = SaFStorageHelper(context)
    
    @Provides
    fun provideDialogHelper(
        activity: Activity,
        safHelper: SaFStorageHelper
    ): TellingDialogHelper = TellingDialogHelper(activity, activity, safHelper)
    
    @Singleton
    @Provides
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    @Singleton
    @Provides
    fun provideTrektellenApi(client: OkHttpClient): TrektellenApi = TrektellenApi(client)
}

// 2. ViewModel met Hilt injection
@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val logManager: TellingLogManager,
    private val safHelper: SaFStorageHelper,
    private val api: TrektellenApi,
    private val speechHandler: TellingSpeechHandler,
) : AndroidViewModel(application) {
    // Alle dependencies automatisch injecteerd!
}

// 3. Activity met Hilt
@AndroidEntryPoint
class TellingScherm : AppCompatActivity() {
    private val viewModel: TellingViewModel by viewModels()
    // ViewModel automatically created + injected
}
```

**Voordelen:**
- ✅ Zero manual dependency creation
- ✅ Alle afhankelijkheden expliciет
- ✅ Automatische scoping (Singleton, Activity, etc.)
- ✅ Testable: easy to provide mocks

---

### 1.3 **Limited Feature Modularization**

#### 🔴 PROBLEEM
- Alle code in `:app` module
- Geen feature-isolation
- Moeilijk om features onafhankelijk te testen

#### ✅ MODERNA OPLOSSING: Multi-Module Architecture

```
VoiceTally/
├── :app                 # Main app, orchestration
├── :feature:telling     # Telling recording feature
├── :feature:speech      # Speech recognition
├── :feature:masterClient # Master-client sync
├── :feature:birdnet     # BirdNET integration
├── :core:ui             # Shared UI components
├── :core:network        # API clients
├── :core:data           # Database, repositories
├── :core:common         # Utils, extensions
```

**Build gradle configuration:**

```kotlin
// settings.gradle.kts
include(":app")
include(":feature:telling")
include(":feature:speech")
include(":core:ui")
include(":core:network")
include(":core:data")

// feature/telling/build.gradle.kts
plugins {
    id("com.android.library")
    id("kotlin-android")
}
dependencies {
    api(project(":core:ui"))
    api(project(":core:network"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso)
}
```

**Voordelen:**
- ✅ Features gebundeld + isolated
- ✅ Di-free feature-switching mogelijk
- ✅ Parallele development
- ✅ Betere build-times

---

## 2. 🔄 State Management

### 2.1 **Multiple Sources of Truth**

#### 🔴 PROBLEEM
- **Activity local state** (pendingRecords, tiles, adapters)
- **SharedPreferences** (envelope, credentials, settings)
- **SAF files** (backup, speech logs)
- **ViewModel StateFlows** (recently added)
- **No synchronization** between layers

```kotlin
// Huidig: kaos
private val pendingRecords = mutableListOf<ServerTellingDataItem>() // Activity
if (::viewModel.isInitialized) viewModel.setPendingRecords(pendingRecords.toList()) // ViewModel
prefs.edit().putString("pref_saved_envelope_json", ...) // SharedPreferences
envelopePersistence.saveEnvelopeWithRecords(records) // Files
```

**Impact:**
- Race conditions
- Data loss op crash
- UI out-of-sync

#### ✅ MODERNA OPLOSSING: Single Source of Truth in ViewModel + DataStore

**Stap 1: Migreer SharedPreferences naar DataStore**

```kotlin
// 1. Define Proto schema
// app/src/main/proto/telling_preferences.proto
syntax = "proto3";

message TellingPreferences {
    string saved_envelope_json = 1;
    string online_id = 2;
    string telling_id = 3;
    int64 tile_tap_window_ms = 4;
}

// 2. Create DataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.tellingPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "telling_preferences"
)

// 3. Create Repository
@Singleton
class TellingRepository @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.tellingPreferences
    
    val savedEnvelopeJson: Flow<String> = dataStore.data.map {
        it[stringPreferencesKey("saved_envelope")] ?: ""
    }
    
    suspend fun saveTellingEnvelope(envelope: ServerTellingEnvelope) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("saved_envelope")] = Json.encodeToString(envelope)
        }
    }
}

// 4. Expose in ViewModel
@HiltViewModel
class TellingViewModel @Inject constructor(
    private val repository: TellingRepository
) : AndroidViewModel(application) {
    val savedEnvelope: Flow<ServerTellingEnvelope> = repository.savedEnvelopeJson
        .map { if (it.isBlank()) null else Json.decodeFromString(it) }
        .filterNotNull()
}
```

**Stap 2: Centralize pending records in ViewModel**

```kotlin
@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val repository: TellingRepository
) : AndroidViewModel(application) {
    
    // SINGLE source of truth
    private val _pendingRecords = MutableStateFlow<List<ServerTellingDataItem>>(emptyList())
    val pendingRecords: StateFlow<List<ServerTellingDataItem>> = _pendingRecords.asStateFlow()
    
    private val _tiles = MutableStateFlow<List<SoortRow>>(emptyList())
    val tiles: StateFlow<List<SoortRow>> = _tiles.asStateFlow()
    
    init {
        // Load persisted data on startup
        viewModelScope.launch {
            repository.loadPendingRecords().collect { records ->
                _pendingRecords.value = records
            }
        }
    }
    
    fun addRecord(record: ServerTellingDataItem) {
        val updated = _pendingRecords.value + record
        _pendingRecords.value = updated
        // Persist async
        viewModelScope.launch {
            repository.savePendingRecords(updated)
        }
    }
}
```

**Stap 3: Activity observeert ENKEL**

```kotlin
@AndroidEntryPoint
class TellingScherm : AppCompatActivity() {
    private val viewModel: TellingViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pendingRecords.collect { records ->
                        updateFinalsUI(records)
                    }
                }
                launch {
                    viewModel.tiles.collect { tiles ->
                        tilesAdapter.submitList(tiles)
                    }
                }
            }
        }
    }
}
```

**Voordelen:**
- ✅ Single source of truth
- ✅ Type-safe preferences (DataStore)
- ✅ Automatic persistence
- ✅ Race condition-free

---

### 2.2 **Incomplete ViewModel Integration**

#### 🔴 PROBLEEM
- ViewModel is partial (logs, tiles) maar niet compleet
- Activity maintains local mutable state
- Inconsistent patterns (some use ViewModel, some don't)

#### ✅ MODERNA OPLOSSING: Complete ViewModel Ownership

```kotlin
@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val repository: TellingRepository,
    private val speechController: SpeechInputController,
    private val birdNetController: BirdNetController,
    private val uploadController: UploadController,
    private val tilesManager: TegelBeheer,
) : AndroidViewModel(application) {
    
    // ═══════ UI State ═══════
    private val _tiles = MutableStateFlow<List<SoortRow>>(emptyList())
    val tiles: StateFlow<List<SoortRow>> = _tiles.asStateFlow()
    
    private val _finals = MutableStateFlow<List<SpeechLogRow>>(emptyList())
    val finals: StateFlow<List<SpeechLogRow>> = _finals.asStateFlow()
    
    private val _partials = MutableStateFlow<List<SpeechLogRow>>(emptyList())
    val partials: StateFlow<List<SpeechLogRow>> = _partials.asStateFlow()
    
    private val _pendingRecords = MutableStateFlow<List<ServerTellingDataItem>>(emptyList())
    val pendingRecords: StateFlow<List<ServerTellingDataItem>> = _pendingRecords.asStateFlow()
    
    // ═══════ Flags ═══════
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()
    
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()
    
    // ═══════ User Intents ═══════
    fun recordSpeciesCount(speciesId: String, count: Int) {
        viewModelScope.launch {
            try {
                tilesManager.updateSpecies(speciesId, count)
                _tiles.value = tilesManager.getTiles()
            } catch (e: Exception) {
                _error.value = AppError("Failed to record count", e)
            }
        }
    }
    
    suspend fun uploadFinalized(): Result<String?> {
        return try {
            _isUploading.value = true
            val result = uploadController.upload(_pendingRecords.value)
            result.onSuccess {
                _pendingRecords.value = emptyList()
                _finals.value = emptyList()
            }
            _isUploading.value = false
            result
        } catch (e: Exception) {
            _error.value = AppError("Upload failed", e)
            _isUploading.value = false
            Result.failure(e)
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
```

---

## 3. 💾 Persistent Storage

### 3.1 **SharedPreferences for Everything**

#### 🔴 PROBLEEM
- SharedPreferences for complex data (envelopes, records, aliases)
- Manual JSON serialization/deserialization
- No schema versioning
- No type safety

```kotlin
// Huidig: manual, error-prone
val savedEnvelopeJson = prefs.getString("pref_saved_envelope_json", null)
val envelopeList = VT5App.json.decodeFromString<List<ServerTellingEnvelope>>(savedEnvelopeJson!!)
prefs.edit().putString("pref_saved_envelope_json", enc).apply()
```

#### ✅ MODERNA OPLOSSING: Room Database + DataStore

**Stap 1: Setup Room**

```kotlin
// Add dependency
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}

// Define entities
@Entity(tableName = "tellings")
data class TellingEntity(
    @PrimaryKey val id: String,
    val telpostId: String,
    val begintijd: Long,
    val eindtijd: Long,
    val createdAt: Long,
    val isUploaded: Boolean = false,
    val uploadedAt: Long? = null
)

@Entity(tableName = "observations", foreignKeys = [
    ForeignKey(
        entity = TellingEntity::class,
        parentColumns = ["id"],
        childColumns = ["tellingId"]
    )
])
data class ObservationEntity(
    @PrimaryKey val id: String,
    val tellingId: String,
    val speciesId: String,
    val count: Int,
    val direction: String?,
    val timestamp: Long,
    val notes: String?
)

// Define DAO
@Dao
interface TellingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelling(telling: TellingEntity)
    
    @Query("SELECT * FROM tellings WHERE id = :id")
    fun getTellingFlow(id: String): Flow<TellingEntity?>
    
    @Query("SELECT * FROM observations WHERE tellingId = :tellingId")
    fun getObservationsFlow(tellingId: String): Flow<List<ObservationEntity>>
    
    @Transaction
    @Query("SELECT * FROM tellings WHERE id = :id")
    fun getTellingWithObservations(id: String): Flow<TellingWithObservations>
}

data class TellingWithObservations(
    @Embedded val telling: TellingEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "tellingId"
    )
    val observations: List<ObservationEntity>
)

// Define Database
@Database(
    entities = [TellingEntity::class, ObservationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class VT5Database : RoomDatabase() {
    abstract fun tellingDao(): TellingDao
}

// Provide with Hilt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideVT5Database(
        @ApplicationContext context: Context
    ): VT5Database = Room.databaseBuilder(
        context,
        VT5Database::class.java,
        "vt5_database"
    ).build()
    
    @Provides
    fun provideTellingDao(db: VT5Database) = db.tellingDao()
}
```

**Stap 2: Migrate from SharedPreferences to Room**

```kotlin
@Singleton
class TellingRepository @Inject constructor(
    private val tellingDao: TellingDao,
    private val tellingRemoteDataSource: TellingRemoteDataSource
) {
    
    fun getTellingWithObservemaintain(id: String): Flow<TellingWithObservations?> =
        tellingDao.getTellingWithObservations(id)
    
    suspend fun saveTelling(envelope: ServerTellingEnvelope) {
        val tellingEntity = TellingEntity(
            id = envelope.tellingid,
            telpostId = envelope.telpostid,
            begintijd = envelope.begintijd.toLong(),
            eindtijd = envelope.eindtijd.toLong(),
            createdAt = System.currentTimeMillis()
        )
        tellingDao.insertTelling(tellingEntity)
        
        envelope.data.forEach { record ->
            val obs = ObservationEntity(
                id = record.idLocal,
                tellingId = envelope.tellingid,
                speciesId = record.soortid,
                count = record.aantal.toInt(),
                direction = record.richting,
                timestamp = record.tijdstip.toLong(),
                notes = record.opmerkingen
            )
            tellingDao.insertObservation(obs)
        }
    }
    
    suspend fun getTellingForUpload(tellingId: String): ServerTellingEnvelope? {
        val entity = tellingDao.getTelling(tellingId)?.first() ?: return null
        val observations = tellingDao.getObservations(tellingId).first()
        
        return ServerTellingEnvelope(
            tellingid = entity.id,
            telpostid = entity.telpostId,
            begintijd = entity.begintijd.toString(),
            eindtijd = entity.eindtijd.toString(),
            data = observations.map { obs ->
                ServerTellingDataItem(
                    idLocal = obs.id,
                    soortid = obs.speciesId,
                    aantal = obs.count.toString(),
                    rijksmuseum = "0",
                    // ... map other fields
                )
            }
        )
    }
}
```

**Voordelen:**
- ✅ Type-safe queries
- ✅ Automatic migrations
- ✅ Transactions support
- ✅ Reactive (Flow-based)
- ✅ Complex queries possible

---

### 3.2 **Manual JSON Serialization**

#### 🔴 PROBLEEM
- Try-catch everywhere voor JSON parsing
- No versioning
- Manual nullability handling
- Brittle (breaks on schema changes)

#### ✅ MODERNA OPLOSSING: Proper Serialization with Migration

```kotlin
// 1. Use sealed classes for versioning
@Serializable
sealed class EnvelopeSchema {
    @Serializable
    @SerialName("v1")
    data class V1(
        val tellingid: String,
        val data: List<ObservationV1>
    ) : EnvelopeSchema()
    
    @Serializable
    @SerialName("v2")
    data class V2(
        val tellingid: String,
        val telpostid: String,
        val metadata: MetadataV2,
        val data: List<ObservationV2>
    ) : EnvelopeSchema()
}

// 2. Create extension for safe parsing
inline fun <reified T> Json.decodeFromStringSafe(
    json: String,
    default: T
): T = try {
    decodeFromString(json)
} catch (e: SerializationException) {
    Log.w("JSON", "Failed to parse, using default: ${e.message}")
    default
}

// 3. Migrate on load
fun migrateEnvelope(schema: EnvelopeSchema): ServerTellingEnvelope = when (schema) {
    is EnvelopeSchema.V1 -> ServerTellingEnvelope(
        tellingid = schema.tellingid,
        telpostid = "", // default for v1
        data = schema.data.map { migrateObservationV1toV2(it) }
    )
    is EnvelopeSchema.V2 -> ServerTellingEnvelope(
        tellingid = schema.tellingid,
        telpostid = schema.telpostid,
        data = schema.data
    )
}
```

---

## 4. 🌐 Network & API Communicatie

### 4.1 **Basic Error Handling**

#### 🔴 PROBLEEM
- Catch-all exception handlers
- No retry logic (except via WorkManager)
- No timeout configuration
- Silent failures (Log.w then null)

```kotlin
// Huidig:
try {
    discoveryHttp.newCall(req).execute().use { it.isSuccessful }
} catch (_: Exception) {
    false
}
```

#### ✅ MODERNA OPLOSSING: Proper HTTP Client + Retry Policy

```kotlin
// 1. Create dedicated HTTP clients per use-case
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Singleton
    @Named("api")
    @Provides
    fun provideApiOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(UnauthorizedInterceptor()) // Handle 401
        .addNetworkInterceptor(OfflineInterceptor()) // Handle offline
        .build()
    
    @Singleton
    @Named("sse")
    @Provides
    fun provideSseOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // > heartbeat
        .retryOnConnectionFailure(false) // Custom retry logic
        .build()
    
    @Singleton
    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY 
                    else HttpLoggingInterceptor.Level.NONE
        }
}

// 2. Implement retry logic with exponential backoff
fun <T> OkHttpClient.executeWithRetry(
    request: Request,
    maxRetries: Int = 3,
    backoffMultiplier: Double = 2.0,
    initialDelayMs: Long = 1000L
): Result<T> {
    var delayMs = initialDelayMs
    var lastException: Exception? = null
    
    repeat(maxRetries) { attempt ->
        try {
            val response = newCall(request).execute()
            
            return when {
                response.isSuccessful -> Result.success(response.body?.string() as T)
                response.code == 429 -> {
                    // Rate limited
                    Thread.sleep(delayMs)
                    delayMs = (delayMs * backoffMultiplier).toLong()
                    null // Continue retry
                }
                response.code in 500..599 -> {
                    // Server error, retry
                    Thread.sleep(delayMs)
                    delayMs = (delayMs * backoffMultiplier).toLong()
                    null // Continue retry
                }
                else -> Result.failure(HttpException(response.code, response.message))
            }
        } catch (e: IOException) {
            lastException = e
            if (attempt < maxRetries - 1) {
                Thread.sleep(delayMs)
                delayMs = (delayMs * backoffMultiplier).toLong()
            }
        }
    }
    
    return Result.failure(lastException ?: Exception("Max retries exceeded"))
}

// 3. Create typed API client with suspend functions
@Singleton
class TrektellenApiClient @Inject constructor(
    @Named("api") private val httpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun uploadCounts(
        baseUrl: String,
        username: String,
        password: String,
        envelope: ServerTellingEnvelope
    ): Result<UploadResponse> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(ListSerializer(ServerTellingEnvelope.serializer()), listOf(envelope))
            val request = Request.Builder()
                .url("$baseUrl/api/counts_save?language=dutch&versie=1845")
                .header("Authorization", Credentials.basic(username, password))
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            
            // Use retry wrapper
            val response = httpClient.executeWithRetry<String>(request, maxRetries = 5)
            
            response.mapCatching { responseJson ->
                json.decodeFromString<UploadResponse>(responseJson)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 4. Usage in ViewModel
suspend fun uploadTelling(envelope: ServerTellingEnvelope) {
    _isUploading.value = true
    val result = apiClient.uploadCounts(
        baseUrl = credentials.baseUrl,
        username = credentials.username,
        password = credentials.password,
        envelope = envelope
    )
    
    result
        .onSuccess { response ->
            _uploadSuccess.value = response.onlineId
            _pendingrecords.value = emptyList()
        }
        .onFailure { error ->
            _uploadError.value = AppError("Upload failed", error)
            // Schedule retry via WorkManager
            scheduleRetryUpload()
        }
    
    _isUploading.value = false
}
```

**Voordelen:**
- ✅ Typed responses
- ✅ Automatic retry with exponential backoff
- ✅ Configurable timeouts
- ✅ Error classification
- ✅ No silent failures

---

### 4.2 **Limited SSE Handling**

#### 🔴 PROBLEEM
- BirdNetSseClient restarts on every error
- No reconnection strategy
- No deduplication check
- Manual event parsing

#### ✅ MODERNA OPLOSSING: Robust SSE with Reconnect Logic

```kotlin
class RobustBirdNetSseClient @Inject constructor(
    @Named("sse") private val httpClient: OkHttpClient,
    private val scope: CoroutineScope
) {
    
    sealed class SseEvent {
        data class Detection(val detection: BirdNetPendingDetection) : SseEvent()
        data class Connected(val timestamp: Long = System.currentTimeMillis()) : SseEvent()
        data class Error(val exception: Exception) : SseEvent()
        data class Reconnecting(val attempt: Int) : SseEvent()
    }
    
    private val events = MutableSharedFlow<SseEvent>()
    val evessentials: Flow<SseEvent> = events
    
    suspend fun connect(config: BirdNetConfig): Result<Unit> = withContext(Dispatchers.IO) {
        var reconnectAttempt = 0
        val maxAttempts = 10
        val initialDelay = 1000L
        
        while (reconnectAttempt < maxAttempts) {
            try {
                connectInternal(config)
                reconnectAttempt = 0 // Reset on success
            } catch (e: Exception) {
                reconnectAttempt++
                val delay = getBackoffDelay(reconnectAttempt, initialDelay)
                
                events.emit(SseEvent.Error(e))
                events.emit(SseEvent.Reconnecting(reconnectAttempt))
                
                delay(delay)
            }
        }
        
        Result.failure(Exception("Max SSE reconnection attempts exceeded"))
    }
    
    private suspend fun connectInternal(config: BirdNetConfig) {
        val url = "${config.protocol}://${config.host}:${config.port}/api/v2/listen"
        val request = Request.Builder().url(url).build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.message)
            }
            
            events.emit(SseEvent.Connected())
            
            response.body?.source()?.let { source ->
                val reader = BufferedReader(source.inputStream().reader())
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    line?.let { parseSseLine(it) }?.let { detection ->
                        events.emit(SseEvent.Detection(detection))
                    }
                }
            }
        }
    }
    
    private fun parseSseLine(line: String): BirdNetPendingDetection? = tryOrNull {
        when {
            line.startsWith("data:") -> {
                val json = line.removePrefix("data:").trim()
                Json.decodeFromString<BirdNetPendingDetection>(json)
            }
            else -> null
        }
    }
    
    private fun getBackoffDelay(attempt: Int, initialDelay: Long): Long {
        val exponential = initialDelay * (1 shl (attempt - 1)) // 2^(attempt-1)
        val jitter = Random.nextLong(-1000, 1000)
        return (exponential + jitter).coerceIn(100, 300_000) // Cap at 5 min
    }
}
```

---

## 5. ⏱️ Asynchroon & Coroutines

### 5.1 **Manual Job & Resource Tracking**

#### 🔴 PROBLEEM
- Manual Job cancellation tracking
- Resource leaks (parseJobsByUtteranceId, birdNetPendingTickerJob)
- No structured concurrency
- Race conditions

```kotlin
// Huidig: manual tracking
private val parseJobsByUtteranceId = linkedMapOf<String, Job>()
private var birdNetPendingTickerJob: Job? = null

override fun onDestroy() {
    parseJobsByUtteranceId.values.forEach { it.cancel() }
    birdNetPendingTickerJob?.cancel()
    // Easy to miss some jobs!
}
```

#### ✅ MODERNA OPLOSSING: Structured Concurrency

```kotlin
class TellingViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // All coroutines tied to ViewModel scope
    // Automatically cancelled on cleared()
    
    private val jobTracker = JobTracker()
    
    fun parseHypotheses(utteranceId: String) {
        val job = viewModelScope.launch(Dispatchers.Default) {
            // Parse logic
        }
        jobTracker.track(utteranceId, job)
    }
    
    override fun onCleared() {
        jobTracker.cancelAll() // Explicit cleanup (viewModelScope already cancelled)
        super.onCleared()
    }
}

// Helper to track jobs by ID
class JobTracker {
    private val jobs = ConcurrentHashMap<String, Job>()
    
    fun track(id: String, job: Job) {
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
        }
    }
    
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
    
    fun cancel(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
    }
}
```

---

### 5.2 **Race Conditions in Concurrent Updates**

#### 🔴 PROBLEEM
- Unsynchronized access to mutable collections
- `synchronized(pendingRecords) { }` requires manual locking

```kotlin
// Huidig: synchronized blocks scattered everywhere
synchronized(pendingRecords) {
    pendingRecords.clear()
    pendingRecords.addAll(savedRecords)
}

// But read access not synchronized!
if (pendingRecords.isNotEmpty()) { ... }
```

#### ✅ MODERNA OPLOSSING: Atomic StateFlows

```kotlin
// Use StateFlow instead of mutableList
class TellingViewModel : AndroidViewModel {
    private val _pendingRecords = MutableStateFlow<List<ServerTellingDataItem>>(emptyList())
    val pendingRecords: StateFlow<List<ServerTellingDataItem>> = _pendingRecords.asStateFlow()
    
    // All updates go through state assignment (atomic)
    fun addRecord(record: ServerTellingDataItem) {
        _pendingRecords.value = _pendingRecords.value + record
    }
    
    fun clearRecords() {
        _pendingRecords.value = emptyList()
    }
    
    // No locks needed!
}

// Activity observes
lifecycleScope.launch {
    repeatedOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.pendingRecords.collect { records ->
            updateUI(records)
        }
    }
}
```

---

## 6. 🧪 Testing & Testability

### 6.1 **Tests Disabled**

#### 🔴 PROBLEEM
- Tests completely disabled in build.gradle.kts:
  ```kotlin
  tasks.matching {
      it.name.startsWith("test", true) || it.name.startsWith("connectedAndroidTest", true)
  }.configureEach { this.enabled = false }
  ```
- Zero unit tests
- Zero UI tests
- Impossible to verify refactoring

#### ✅ MODERNA OPLOSSING: Re-enable Tests + Add Testable Architecture

**Stap 1: Enable tests**

```kotlin
// app/build.gradle.kts - REMOVE THIS:
// tasks.matching {
//     it.name.startsWith("test", true) || it.name.startsWith("connectedAndroidTest", true)
// }.configureEach { this.enabled = false }

// Instead, add proper test setup
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-inline:5.1.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
}
```

**Stap 2: Testable ViewModel**

```kotlin
@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val repository: TellingRepository,
    private val uploadController: UploadController
) : AndroidViewModel(application) {
    
    private val _pendingRecords = MutableStateFlow<List<ServerTellingDataItem>>(emptyList())
    val pendingRecords: StateFlow<List<ServerTellingDataItem>> = _pendingRecords.asStateFlow()
    
    fun addRecord(record: ServerTellingDataItem) {
        _pendingRecords.value = _pendingRecords.value + record
    }
}

// Unit test
class TellingViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private val mockRepository = mockk<TellingRepository>(relaxed = true)
    private val mockUploadController = mockk<UploadController>(relaxed = true)
    
    private lateinit var viewModel: TellingViewModel
    
    @Before
    fun setup() {
        viewModel = TellingViewModel(
            Application(),
            mockRepository,
            mockUploadController
        )
    }
    
    @Test
    fun `addRecord increments pending count`() = runTest {
        val record = ServerTellingDataItem(...)
        
        viewModel.addRecord(record)
        
        viewModel.pendingRecords.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(record, list[0])
        }
    }
}
```

**Stap 3: Repository Tests**

```kotlin
class TellingRepositoryTest {
    private lateinit var mockDao: TellingDao
    private lateinit var mockRemoteSource: TellingRemoteDataSource
    private lateinit var repository: TellingRepository
    
    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        mockRemoteSource = mockk(relaxed = true)
        repository = TellingRepository(mockDao, mockRemoteSource)
    }
    
    @Test
    fun `saveTelling inserts into database`() = runTest {
        val envelope = ServerTellingEnvelope(...)
        
        repository.saveTelling(envelope)
        
        verify { mockDao.insertTelling(any()) }
    }
}
```

---

### 6.2 **Tight Coupling Prevents Mocking**

#### 🔴 PROBLEEM
- New keyword ferally used
- No interfaces
- No constructor injection
- Hard to mock speechHandler, tegelBeheer, etc.

#### ✅ MODERNA OPLOSSING: Dependency Injection + Interfaces

```kotlin
// 1. Define contracts
interface SpeechHandler {
    suspend fun initialize()
    fun handleHypotheses(utteranceId: String, hypotheses: List<Pair<String, Float>>)
}

interface SpeciesManager {
    suspend fun updateSpecies(speciesId: String, count: Int)
    suspend fun collectFinalAsRecord(speciesId: String, count: Int)
}

// 2. Inject in ViewModel
@HiltViewModel
class TellingViewModel @Inject constructor(
    application: Application,
    private val speechHandler: SpeechHandler,
    private val speciesManager: SpeciesManager
) : AndroidViewModel(application) {
    // Now dependencies are swappable!
}

// 3. Mock in tests
@Test
fun testSpeechHandling() {
    val mockSpeechHandler = mockk<SpeechHandler>()
    val mockSpeciesManager = mockk<SpeciesManager>()
    
    val viewModel = TellingViewModel(
        Application(),
        mockSpeechHandler,
        mockSpeciesManager
    )
    
    coEvery { mockSpeechHandler.initialize() } just runs
    
    viewModel.initialize()
    
    coVerify { mockSpeechHandler.initialize() }
}
```

---

## 7. 🧹 Code Quality & Maintainability

### 7.1 **God Classes**

#### 🔴 PROBLEEM
- TellingScherm: 3103 lines
- 30+ lateinit vars
- 5+ responsibilities
- Impossible to test unit

#### ✅ MODERNA OPLOSSING: (Already covered in Arch section)
- Extract Controllers
- Use ViewModel as state container
- Break into smaller classes per feature

---

### 7.2 **Inconsistent Error Handling**

#### 🔴 PROBLEEM
- `try { } catch (_: Exception) { Log.w(...) }` everywhere
- No user feedback for all errors
- Silent failures

```kotlin
try {
    discoveryHttp.newCall(req).execute().use { it.isSuccessful }
} catch (_: Exception) { // Silent!
    false
}
```

#### ✅ MODERNA OPLOSSING: Proper Error Sealed Classes

```kotlin
sealed class AppError {
    data class NetworkError(val message: String, val cause: Exception) : AppError()
    data class SerializationError(val message: String) : AppError()
    data class AuthError(val message: String) : AppError()
    data class ValidationError(val message: String) : AppError()
    data class UnknownError(val message: String, val cause: Exception) : AppError()
}

sealed class Result<T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error<T>(val error: AppError) : Result<T>()
}

suspend fun discoverBirdNetHost(): Result<BirdNetConfig> = try {
    val result = BirdNetDiscovery.discover()
    Result.Success(result.toConfig())
} catch (e: IOException) {
    Result.Error(AppError.NetworkError("Discovery failed", e))
} catch (e: SerializationException) {
    Result.Error(AppError.SerializationError("Invalid response"))
}

// In ViewModel
fun handleDiscovery() {
    viewModelScope.launch {
        when (val result = birdNetClient.discover()) {
            is Result.Success -> _birdNetConfig.value = result.data
            is Result.Error -> _error.value = result.error // User sees feedback
        }
    }
}
```

---

### 7.3 **No Constants & Magic Strings**

#### 🔴 PROBLEEM
```kotlin
val now = System.currentTimeMillis()
delay(1000L) // What is this for?
if (now - lastPartialUiUpdateMs >= 200L) { ... } // Where does 200 come from?
```

#### ✅ MODERNA OPLOSSING

```kotlin
object TellingConstants {
    // UI Constants
    const val PARTIAL_UI_DEBOUNCE_MS = 200L
    const val PARTIAL_LOG_VISIBLE_DURATION_MS = 5000L
    const val SUCCESS_DIALOG_DISMISS_DELAY_MS = 1000L
    
    // Network
    const val API_TIMEOUT_SECONDS = 30L
    const val SSE_HEARTBEAT_INTERVAL_MS = 30_000L
    const val MAX_RETRY_ATTEMPTS = 5
    
    // Storage
    const val DATABASE_VERSION = 1
    const val PREFERENCE_FILE_NAME = "vt5_preferences"
    const val MAX_LOG_ROWS = 600
    
    // Features
    const val TILE_TAP_GROUP_WINDOW_MS = 5_000L
    const val MAX_RECENTS = 30
}

// Usage
delay(TellingConstants.SUCCESS_DIALOG_DISMISS_DELAY_MS)
if (now - lastPartialUiUpdateMs >= TellingConstants.PARTIAL_UI_DEBOUNCE_MS) { ... }
```

---

## 8. 🤖 Android Modern Best Practices

### 8.1 **SharedPreferences vs DataStore**

#### 🔴 PROBLEEM
- SharedPreferences sync on main thread
- Type-unsafe
- No observability

#### ✅ MODERNA OPLOSSING
- (Already covered above)
- Use Proto DataStore for typed preferences
- All updates asynchronous

---

### 8.2 **No Room/SQLite**

#### 🔴 PROBLEEM
- Complex data stored as JSON strings
- No schema versioning
- No transactions

#### ✅ MODERNA OPLOSSING
- (Already covered above)
- Define Room entities
- Use migrations for schema changes

---

### 8.3 **Fragment Usage**

#### 🔴 PROBLEEM
- All content in Activities
- No fragment reuse
- Navigation tied to Activity

#### ✅ MODERNA OPLOSSING: Use Jetpack Navigation

```kotlin
// 1. Define navigation graph
// res/navigation/telling_nav_graph.xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/telling_nav_graph"
    app:startDestination="@id/tel lingFragment">
    
    <fragment
        android:id="@+id/tellingFragment"
        android:name="com.yvesds.vt5.features.telling.ui.TellingFragment"
        android:label="Telling"/>
    
    <fragment
        android:id="@+id/annotationFragment"
        android:name="com.yvesds.vt5.features.telling.ui.AnnotationFragment"
        android:label="Annotation"/>
</navigation>

// 2. Create Fragment
class TellingFragment : Fragment(R.layout.fragment_telling) {
    private val viewModel: TellingViewModel by viewModels()
    private val navController by lazy { findNavController() }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Observe ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingRecords.collect { records ->
                    updateUI(records)
                }
            }
        }
    }
}

// 3. Navigate
navController.navigate(R.id.action_tellingFragment_to_annotationFragment, bundleOf())
```

---

### 8.4 **No Compose**

#### 🔴 PROBLEEM
- XML layouts hard to maintain
- ViewBinding boilerplate
- Not reactive

#### ✅ MODERNA OPLOSSING: Jetpack Compose (Future)

```kotlin
// Could be migrated to Compose gradually
@Composable
fun TellingScreen(
    viewModel: TellingViewModel = hiltViewModel()
) {
    val pendingRecords by viewModel.pendingRecords.collectAsState(emptyList())
    val isUploading by viewModel.isUploading.collectAsState(false)
    
    Column(modifier = Modifier.fillMaxSize()) {
        TilesGrid(records = pendingRecords)
        FinalsList(records = pendingRecords)
        
        if (isUploading) {
            LinearProgressIndicator()
        }
        
        Button(onClick = { viewModel.upload() }) {
            Text("Afronden")
        }
    }
}
```

---

## 9. 🔐 Security

### 9.1 **No Secret Management**

#### 🔴 PROBLEEM
- Credentials in SharedPreferences (partially encrypted)
- API keys hardcoded
- No secure token storage

#### ✅ MODERNA OPLOSSING: EncryptedSharedPreferences + Jetpack Security

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Singleton
    @Provides
    fun provideCredentialsStore(
        @ApplicationContext context: Context
    ): CredentialsStore = CredentialsStore(context)
}

@Singleton
class CredentialsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secret_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveCredentials(username: String, password: String) {
        encryptedPrefs.edit {
            putString("username", username)
            putString("password", password)
        }
    }
    
    fun getCredentials(): Pair<String, String>? {
        val username = encryptedPrefs.getString("username", null)
        val password = encryptedPrefs.getString("password", null)
        return if (username != null && password != null) username to password else null
    }
}
```

---

### 9.2 **No Input Validation**

#### 🔴 PROBLEEM
- User input not sanitized
- SQL-injection-like risks in filters
- Cross-site scripting NOT applicable but data integrity issues

#### ✅ MODERNA OPLOSSING: Input Validation Layer

```kotlin
sealed class ValidationError {
    object EmptyField : ValidationError()
    object InvalidFormat : ValidationError()
    object TooLong : ValidationError()
}

data class ValidatedRecord(
    val speciesId: String,
    val count: Int,
    val notes: String
)

object RecordValidator {
    fun validateAndCreate(
        speciesId: String?,
        count: String?,
        notes: String?
    ): Result<ValidatedRecord, ValidationError> {
        if (speciesId.isNullOrBlank()) return Result.Error(ValidationError.EmptyField)
        if (count.isNullOrBlank()) return Result.Error(ValidationError.EmptyField)
        
        val countInt = count.toIntOrNull() ?: return Result.Error(ValidationError.InvalidFormat)
        if (countInt < 0) return Result.Error(ValidationError.InvalidFormat)
        
        val sanitizedNotes = notes?.take(500) ?: ""
        
        return Result.Success(ValidatedRecord(speciesId, countInt, sanitizedNotes))
    }
}
```

---

## 10. ⚡ Performance & Resource Management

### 10.1 **No Pagination**

#### 🔴 PROBLEEM
- All records loaded into memory
- No lazy loading
- UI lags with large datasets

#### ✅ MODERNA OPLOSSING: Paging Library 3

```kotlin
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState

class ObservationPagingSource(
    private val tellingId: String,
    private val dao: ObservationDao
) : PagingSource<Int, ObservationEntity>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ObservationEntity> {
        return try {
            val page = params.key ?: 0
            val items = dao.getObservationsPaged(tellingId, page * PAGE_SIZE, PAGE_SIZE)
            
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.size < PAGE_SIZE) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, ObservationEntity>): Int? = null
    
    companion object {
        const val PAGE_SIZE = 50
    }
}

@Singleton
class ObservationRepository @Inject constructor(
    private val dao: ObservationDao
) {
    fun getObservationsPaged(tellingId: String): Flow<PagingData<ObservationEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { ObservationPagingSource(tellingId, dao) }
        ).flow
    }
}

// In ViewModel
@HiltViewModel
class TellingViewModel @Inject constructor(
    private val repository: ObservationRepository
) : AndroidViewModel(application) {
    
    val observations: Flow<PagingData<ObservationEntity>> =
        repository.getObservationsPaged(tellingId = "current")
}

// In UI
lifecycleScope.launch {
    viewModel.observations.collectLatest { pagingData ->
        adapter.submitData(pagingData)
    }
}
```

---

### 10.2 **Heavy Main Thread Work**

#### 🔴 PROBLEEM
- JSON parsing on main**
- File I/O on main
- Speech processing on main

#### ✅ MODERNA OPLOSSING: Explicit Dispatcher Management

```kotlin
// Always specify dispatcher explicitly
viewModelScope.launch(Dispatchers.Default) {
    // CPU-intensive: JSON parsing, filtering, sorting
    val processed = records
        .filter { it.isValid }
        .sortedBy { it.timestamp }
    
    withContext(Dispatchers.Main) {
        // Update UI
        _records.value = processed
    }
}

viewModelScope.launch(Dispatchers.IO) {
    // File/DB I/O
    val saved = repository.saveTelling(envelope)
    
    withContext(Dispatchers.Main) {
        _uploadResult.value = saved
    }
}
```

---

### 10.3 **Multiple HTTP Clients**

#### 🔴 PROBLEEM
- Each feature creates own OkHttpClient
- No connection pooling reuse
- Memory waste

#### ✅ MODERNA OPLOSSING: Singleton HTTP Client + Interceptors

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Singleton
    @Provides
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectionPool(ConnectionPool(maxIdleConnections = 5, keepAliveDuration = 5, TimeUnit.MINUTES))
        .build()
    
    @Provides
    fun provideAuthInterceptor(credentialsStore: CredentialsStore): AuthInterceptor =
        AuthInterceptor(credentialsStore)
    
    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY 
                    else HttpLoggingInterceptor.Level.NONE
        }
}

// All clients use same instance
@Provides
fun provideTrektellenApi(@Named("api") client: OkHttpClient) = TrektellenApi(client)

@Provides
fun provideBirdNetClient(@Named("api") client: OkHttpClient) = BirdNetClient(client)
```

---

## 11. 📊 Logging & Monitoring

### 11.1 **Basic Log Usage**

#### 🔴 PROBLEEM
- `Log.d()` / `Log.w()` scattered everywhere
- No structured logging
- No backend integration
- Difficult to debug production issues

#### ✅ MODERNA OPLOSSING: Structured Logging + Crash Reporting

**Stap 1: Add Firebase Crashlytics**

```kotlin
// gradle
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.0.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}

// Module
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    
    @Singleton
    @Provides
    fun provideCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
    
    @Provides
    fun provideLogger(crashlytics: FirebaseCrashlytics): Logger = FirebaseLogger(crashlytics)
}

interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, exception: Throwable? = null)
    fun w(tag: String, message: String)
}

class FirebaseLogger(private val crashlytics: FirebaseCrashlytics) : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
        crashlytics.log(message)
    }
    
    override fun e(tag: String, message: String, exception: Throwable?) {
        Log.e(tag, message, exception)
        crashlytics.recordException(exception ?: Exception(message))
    }
    
    override fun w(tag: String, message: String) {
        Log.w(tag, message)
        crashlytics.log("WARNING | $tag: $message")
    }
}
```

**Stap 2: Use in ViewModel**

```kotlin
@HiltViewModel
class TellingViewModel @Inject constructor(
    private val logger: Logger,
    private val repository: TellingRepository
) : AndroidViewModel(application) {
    
    fun uploadTelling(envelope: ServerTellingEnvelope) {
        viewModelScope.launch {
            try {
                logger.d(TAG, "Starting upload for ${envelope.tellingid}")
                val result = repository.upload(envelope)
                logger.d(TAG, "Upload successful")
            } catch (e: Exception) {
                logger.e(TAG, "Upload failed", e)
                _error.value = "Upload failed: ${e.message}"
            }
        }
    }
}
```

---

## 12. 📚 Documentation & Developer Experience

### 12.1 **Limited Documentation**

#### 🔴 PROBLEEM
- No architecture docs
- No API documentation
- No onboarding guide
- Inline comments rare

#### ✅ MODERNA OPLOSSING

**Creëer architecture.md:**

```markdown
# VoiceTally Architecture

## High-Level Overview
- **Data Layer:** Room database + DataStore
- **Repository Layer:** TellingRepository, SpeechRepository
- **ViewModel Layer:** TellingViewModel (state container)
- **UI Layer:** Fragments + Jetpack Compose (future)
- **Feature Controllers:** SpeechInputController, BirdNetController, UploadController

## Data Flow
1. User records species count → tile tap
2. TileTapAggregationManager groups taps (5s window)
3. TellingViewModel.addRecord() → StateFlow update
4. Fragment observes → UI updates
5. On finalize: UploadController uploads to API

## Testing Strategy
- Unit tests: ViewModels, Repositories, Controllers (with Hilt)
- UI tests: Compose tests (future)
- Integration tests: DAO + Repository
```

**Creëer CONTRIBUTING.md:**

```markdown
# Contributing Guide

## Setup
1. Clone repository
2. Open in Android Studio 2024+
3. Sync Gradle
4. Build succeeds ✓

## Code Style
- Follow Kotlin style guide
- Use ktlint (IDE integration available)
- 100-character line limit

## Creating a Feature
1. Create feature module `:feature:yourFeature`
2. Define contracts (interfaces)
3. Implement with Hilt injection
4. Add unit tests
5. PR with tests passing

## Commit Messages
```
feat: Add observation edit screen
fix: Resolve race condition in upload
refactor: Extract SpeechController
test: Add TellingViewModel tests
docs: Update architecture guide
```
```

---

## 📋Summary Van Alle Problemen + Aanbevelingen

| Categorie | Probleem | Score | Aanbeveling | Complexiteit |
|-----------|----------|-------|-------------|--------------|
| **Arch** | Godclass TellingScherm | 🔴 | Controllers + ViewModel | High |
| **Arch** | No DI | 🔴 | Hilt | High |
| **Arch** | No modularization | 🟡 | Multi-module | High |
| **State** | Multiple sources of truth | 🔴 | Single ViewModel + DataStore | High |
| **Storage** | SharedPreferences everywhere | 🟡 | Room + DataStore | Medium |
| **Storage** | Manual JSON | 🟡 | Serialization libs + migration | Medium |
| **Network** | Basic error handling | 🟡 | Retry + typed clients | Medium |
| **Network** | Manual SSE retry | 🟡 | Robust reconnect logic | Medium |
| **Async** | Manual Job tracking | 🟡 | Structured concurrency | Low |
| **Async** | Race conditions | 🔴 | StateFlow instead of synchronized | Medium |
| **Testing** | Tests disabled | 🔴 | Re-enable + testable arch | High |
| **Code Quality** | God classes | 🔴 | Extract controllers | High |
| **Code Quality** | Inconsistent error handling | 🟡 | Sealed error types | Medium |
| **Code Quality** | Magic strings | 🔴 | Constants object | Low |
| **Android** | No DataStore | 🟡 | Migrate from SharedPref | Medium |
| **Android** | No Room | 🟡 | Add Room for tellings | Medium |
| **Android** | No fragments | 🟡 | Navigation graph | Medium |
| **Security** | No proper secret mgmt | 🟡 | EncryptedSharedPrefs + Hilt | Low |
| **Performance** | No pagination | 🟡 | Paging library | Medium |
| **Performance** | Heavy main thread | 🟡 | Explicit dispatchers | Low |
| **Logging** | No structured logging | 🟡 | Firebase Crashlytics | Low |
| **Docs** | Limited documentation | 🟡 | Add architecture.md + guide | Low |

---

## 🚀 Gefaseerd Refactoring Roadmap

### **Fase 1: Foundation (Weeks 1-2)**
- [ ] Setup Hilt dependency injection
- [ ] Re-enable tests
- [ ] Add logging infrastructure (Firebase)
- [ ] Create architecture.md

### **Fase 2: State Management (Weeks 3-4)**
- [ ] Migrate SharedPreferences → DataStore
- [ ] Centralize state in complete ViewModel
- [ ] Remove synchronized blocks
- [ ] Add basic unit tests

### **Fase 3: Storage (Weeks 5-6)**
- [ ] Setup Room database
- [ ] Create TellingEntity + ObservationEntity
- [ ] Migrate file I/O → Room
- [ ] Add migration tests

### **Fase 4: Architecture (Weeks 7-10)**
- [ ] Extract SpeechInputController
- [ ] Extract BirdNetController
- [ ] Extract UploadController
- [ ] Reduce TellingScherm to ~300 lines
- [ ] Add feature-level tests

### **Fase 5: Network (Weeks 11-12)**
- [ ] Implement retry logic with exponential backoff
- [ ] Create typed API clients
- [ ] Add error classification
- [ ] Add network tests

### **Fase 6: Polish (Weeks 13+)**
- [ ] Create feature modules
- [ ] Add Jetpack Compose UI (gradual)
- [ ] Performance profiling
- [ ] Production monitoring setup

---

## 🎯 Success Metrics

- ✅ TellingScherm < 300 lines
- ✅ Code coverage > 70%
- ✅ Build time < 30s
- ✅ Zero warnings
- ✅ All tests passing
- ✅ Crash-free sessions in production
- ✅ App startup < 2s

---

**Geschat effort:**  
- **Solo:** 12-16 weken (Phase 1-5)
- **Team (2-3):** 6-8 weken parallelle work
- **Ongoing:** Phase 6 (modularization + Compose) = 4+ weken

Dit document dient als **living roadmap** — update na elke fase.

