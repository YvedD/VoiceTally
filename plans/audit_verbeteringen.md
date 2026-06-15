# VT5 (VoiceTally) Codebase Audit — Verbeterpunten

> **Datum:** 2026-06-13  
> **Doel:** Identificeren van relevante verbeteringen op vlak van moderne technieken en snelheidswinsten.  
> **Let op:** Géén code, enkel audit.

---

## 1. Build & Configuratie

| # | Categorie | Probleem | Impact | Voorgestelde verbetering |
|---|-----------|---------|--------|--------------------------|
| 1.1 | **Release optimalisatie** | `isMinifyEnabled = false` in release build type | APK is ~2-3x groter dan nodig; code niet geobfusceerd; geen dead-code elimination | Schakel R8 minification in (`isMinifyEnabled = true`) mét ProGuard rules |
| 1.2 | **Dependency management** | Version catalog ([`libs.versions.toml`](gradle/libs.versions.toml)) bevat Compose-dependencies (`composeBom`, `material3`, `ui`, etc.) maar `compose = false` in `buildFeatures` | Onnodige dependencies in de classpath; verwarrend | Verwijder overbodige Compose-entries uit de catalogus |
| 1.3 | **Baseline Profiles** | Er staat wél een `baselineProfiles/` directory in de release folder, maar geen generatie in [`app/build.gradle.kts`](app/build.gradle.kts) | Baseline Profiles worden niet gegenereerd, dus geen AOT-optimalisatie bij installatie | Voeg de `baselineProfileGenerator` plugin toe en genereer profielen |
| 1.4 | **Gradle parallel build** | `org.gradle.parallel=true` staat uit in [`gradle.properties`](gradle.properties) | Builds zijn trager dan mogelijk | Zet parallel build aan |
| 1.5 | **Non-transitive R class** | `android.nonTransitiveRClass=true` staat in [`gradle.properties`](gradle.properties:23) — dit is al default sinds AGP 8.0 | Overbodige flag | Kan verwijderd worden (maar geen urgentie) |

---

## 2. Architectuur & Patterns

| # | Categorie | Probleem | Impact | Voorgestelde verbetering |
|---|-----------|---------|--------|--------------------------|
| 2.1 | **God-class** | [`TellingScherm.kt`](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt) is **3123 lijnen** met ~20 helper dependencies | Extreem moeilijk te onderhouden, testen en debuggen; single-responsibility geschonden | Verdeel over meerdere gespecialiseerde schermen of gebruik een propere MVVM/MVI-architectuur |
| 2.2 | **Geen Dependency Injection** | Alle dependencies worden manueel aangemaakt via `lateinit var` + `initializeHelpers()` | Fragiele lifecycle; moeilijk te testen; geen scoping van dependencies | Implementeer Hilt of Koin voor DI |
| 2.3 | **ViewModel als "mirror"** | [`TellingViewModel`](app/src/main/java/com/yvesds/vt5/features/telling/TellingViewModel.kt) wordt gebruikt als passieve spiegel van de activity-state, niet als echte state holder met business logic | State management is onduidelijk; rotation recovery is fragiel | ViewModel moet de enige source of truth zijn; UI is een observable |
| 2.4 | **Geen Navigation Component** | Navigatie gebeurt via `startActivity(Intent(...))` met `FLAG_ACTIVITY_CLEAR_TOP` etc. | Geen type-safe navigation; moeilijk om argumenten en backstack te beheren | Migreer naar Jetpack Navigation |
| 2.5 | **Geen Repository pattern** | Data access (ServerDataCache, AliasManager, etc.) is verspreid over de codebase | Tight coupling tussen data sources en UI | Centraliseer data access achter repository interfaces |
| 2.6 | **Master-Client architectuur** | Eigen TCP-server (`MasterServer`) op raw sockets met zelf-ontworpen JSON-protocol | Veiligheid, stabiliteit en herconnectie zijn volledig eigen verantwoordelijkheid | Overweeg WebSockets of een gestandaardiseerd protocol; voeg reconnectie met exponential backoff toe |

---

## 3. Prestatie & Snelheid

| # | Categorie | Probleem | Bestand/Locatie | Voorgestelde verbetering |
|---|-----------|---------|-----------------|--------------------------|
| 3.1 | **SSE reconnect loop** | [`BirdNetSseClient`](app/src/main/java/com/yvesds/vt5/features/birdnet/BirdNetSseClient.kt) pending ticker maakt een **nieuwe SSE-verbinding** elke cyclus i.p.v. persistent | Veel overhead bij verbindingsherenvatting; geen backoff bij falen | Houd één persistente SSE-verbinding; implementeer exponential backoff bij disconnect |
| 3.2 | **File I/O bij elke observatie** | [`TellingEnvelopePersistence.saveEnvelopeWithRecords()`](app/src/main/java/com/yvesds/vt5/features/telling/TellingEnvelopePersistence.kt:111) schrijft **volledige JSON** naar SAF + internal fallback **bij elke waarneming** | SAF is traag (ContentResolver); dit kan UI-jank veroorzaken bij snel tellen | Limiteer persist-frequentie (debounce 1-2s); schrijf enkel naar internal, sync naar SAF op achtergrond |
| 3.3 | **String concatenatie in loops** | [`formatBirdNetPendingTickerText()`](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt:779) gebruikt `joinToString` met sort + `take(6)` — wordt elke pending-update opnieuw gesorteerd | Onnodige sorting overhead bij elke ticker-update | Caching van gesorteerde lijst; sorteer enkel bij nieuwe data |
| 3.4 | **NumberPicker vs slider** | [`InstellingenScherm`](app/src/main/java/com/yvesds/vt5/hoofd/InstellingenScherm.kt:194) gebruikt `NumberPicker` (10-30) voor lettergroottes | `NumberPicker` is een zware View met eigen scrol-gedrag | Vervang door `SeekBar` (slider) voor lichtere UI |
| 3.5 | **JSON parsing overhead** | `parseOnlineIdFromResponse()` in [`TellingUploadCore`](app/src/main/java/com/yvesds/vt5/features/telling/TellingUploadCore.kt:271) parseert JSON response **drie keer** (eerst structuur, dan regex, dan digit fallback) | Dubbele parsing bij elke upload-response | Één gestructureerde parse, daarna pas fallbacks |
| 3.6 | **MutableList synchronisatie** | `synchronized(pendingRecords)` op een gewone `mutableListOf()` in [`TellingScherm`](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt:151) | `synchronized` is geen garantie voor iteratie-safety; `toString()`, `toList()` kopieën zijn dure operaties op de main thread | Gebruik `ConcurrentLinkedQueue` of `MutableStateFlow` met atomische updates |
| 3.7 | **Recursive color apply** | [`UiColorPrefs.applyTextColorRecursive()`](app/src/main/java/com/yvesds/vt5/core/ui/UiColorPrefs.kt:118) doorloopt **alle** `ViewGroup`-kinderen recursief bij elke themawissel | Kan traag zijn bij complexe layouts | Gebruik `ViewTreeObserver` of een thema-aanpak op applicatieniveau |

---

## 4. Codekwaliteit & Onderhoudbaarheid

| # | Categorie | Probleem | Bestand/Locatie | Voorgestelde verbetering |
|---|-----------|---------|-----------------|--------------------------|
| 4.1 | **Magic strings** | SharedPreferences keys zoals `"pref_saved_envelope_json"`, `"pref_online_id"` worden als raw strings doorgegeven | Typo's worden pas runtime ontdekt; geen IDE auto-completion | Centraliseer alle prefs keys in één object/companion |
| 4.2 | **Broad exception catching** | Overmatig gebruik van `catch (e: Exception) { Log.w(...) }` — soms zelfs `catch (_: Exception) {}` | Verbergt echte fouten; maakt debugging moeilijk | Vang specifieke excepties; overweeg `runCatching` met expliciete error handling |
| 4.3 | **lateinit + ::isInitialized** | Veelvuldig gebruik van `::isInitialized` checks voor `lateinit var`'s | Fragiel; verhoogt kans op runtime crashes; moeilijk te refactoren | Gebruik `by lazy` of nullable types met propere null-check |
| 4.4 | **Dode code** | `@Suppress("unused")` op [`_keepGetMaxFavorieten`](app/src/main/java/com/yvesds/vt5/hoofd/InstellingenScherm.kt:118) — private constante die nergens gebruikt wordt | Onnodige code in de codebase | Verwijder dode code |
| 4.5 | **Te grote functies** | [`onCreate()` in TellingScherm](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt:260) is ~100 lijnen met vele initialisaties | Moeilijk leesbaar; schendt single responsibility | Breek op in kleinere, benoemde initialisatiestappen |
| 4.6 | **Gemixte Nederlands/Engels** | Codebase gebruikt zowel Nederlands (`TellingScherm`, `Afronden`, `TegelBeheer`) als Engels (`DataUploader`, `SpeechLogAdapter`, `MatchResult`) | Inconsistente code-stijl; moeilijker voor internationale bijdragen | Kies één taal (bij voorkeur Engels voor code) en consistentie |

---

## 5. Moderne Android Practices

| # | Categorie | Probleem | Huidig | Voorgestelde verbetering |
|---|-----------|---------|--------|--------------------------|
| 5.1 | **SharedPreferences → DataStore** | Alle voorkeuren via `SharedPreferences` (type-onveilig, sync op main thread) | `context.getSharedPreferences(...)` met `.edit { putInt(...) }` | Migreer naar Jetpack DataStore (type-safe, coroutine-based) |
| 5.2 | **Geen Room/SQLite** | Lokale persistentie via JSON-bestanden in SAF en SharedPreferences | `ServerTellingDataItem` wordt als JSON-string bewaard | Gebruik Room voor gestructureerde data (tellingen, waarnemingen, aliassen) |
| 5.3 | **Geen Kotlin Flow** | State updates via `ViewModel` met `LiveData` of manuele `adapter.submitList()` | Mixed LiveData + imperative approach | Migreer naar `StateFlow`/`SharedFlow` voor reactieve streams |
| 5.4 | **Manuele BroadcastReceiver** | Alias reload via `BroadcastReceiver` + `IntentFilter` | [`aliasReloadReceiver`](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt:206) | Vervang door `SharedFlow` in een shared ViewModel of DI-service |
| 5.5 | **Geen Jetpack Compose** | Volledige UI in XML + ViewBinding | `viewBinding = true`, `compose = false` | Overweeg gefaseerde migratie naar Compose voor nieuwe schermen |
| 5.6 | **WorkManager onderbenut** | [`work-runtime-ktx`](app/build.gradle.kts:75) staat in dependencies, maar wordt niet gebruikt voor background uploads | `WorkManager` is geïmporteerd maar uploads gebeuren direct op de main coroutine | Gebruik WorkManager voor retry-able uploads (netwerkafhankelijk) |
| 5.7 | **Geen Jetpack Security** | Credentials opgeslagen in plain SharedPreferences | [`CredentialsStore`](app/src/main/java/com/yvesds/vt5/core/secure/CredentialsStore.kt) gebruikt `security-crypto:1.1.0-alpha06` — een alpha-versie! | Gebruik stabiele EncryptedSharedPreferences of Android Keystore |

---

## 6. Beveiliging

| # | Categorie | Probleem | Impact | Voorgestelde verbetering |
|---|-----------|---------|--------|--------------------------|
| 6.1 | **Alpha crypto library** | [`security-crypto:1.1.0-alpha06`](app/build.gradle.kts:62) — alfa-versie in productie | Mogelijke onopgeloste beveiligingslekken; breaking changes bij upgrade | Upgrade naar stabiele versie of gebruik `androidx.security:security-crypto:1.0.0` |
| 6.2 | **Geen HTTPS pinning** | OkHttp client in [`VT5App.http`](app/src/main/java/com/yvesds/vt5/VT5App.kt:197) heeft geen certificate pinning | Man-in-the-middle mogelijk bij API-verkeer | Voeg `CertificatePinner` toe voor `trektellen.nl` en andere endpoints |
| 6.3 | **Geen Network Security Config** | `network_security_config.xml` bestaat maar wordt niet gebruikt in manifest? | Checken of cleartext traffic (http) naar BirdNET wordt toegestaan | Zorg dat network config correct is geconfigureerd met `android:networkSecurityConfig` |

---

## 7. Betrouwbaarheid

| # | Categorie | Probleem | Bestand/Locatie | Voorgestelde verbetering |
|---|-----------|---------|-----------------|--------------------------|
| 7.1 | **Coroutine leak risico** | `lifecycleScope.launch { withContext(Dispatchers.Default) { ... } }` kan blijven dragen na activity destroy | [`TellingScherm`](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt:324) — worker op Default terwijl scope gekoppeld is aan lifecycle | Gebruik `lifecycleScope` correct; overweeg `viewModelScope` voor langere taken |
| 7.2 | **Geen exponential backoff** | BirdNET SSE reconnectie hervat met vaste 1.5s/4s delay | [`startBirdNetPendingTickerIfNeeded()`](app/src/main/java/com/yvesds/vt5/features/telling/TellingScherm.kt:617) | Implementeer backoff: 1s → 2s → 4s → 8s → max 30s |
| 7.3 | **SAF folder creation race** | Meerdere componenten proberen dezelfde SAF-mappen aan te maken (DataUploader, TellingEnvelopePersistence, TellingBackupManager) | Race conditions; dubbele folder creatie | Centraliseer SAF-initialisatie in één service/manager |
| 7.4 | **Geen upload queue monitoring** | Pending upload queue in SharedPreferences wordt niet gecontroleerd op grootte | Queue kan onbeperkt groeien bij netwerkproblemen | Monitor queue grootte; limiet met cleanup-logica |
| 7.5 | **Geen connectivity checks** | Uploads proberen blindelings zonder voorafgaande netwerkcheck | Netwerk exceptions bij elke upload poging bij offline zijn | Gebruik `ConnectivityManager` met `NetworkCallback` voor upload scheduling |

---

## 8. Code Duplicatie

| # | Categorie | Omschrijving | Bestanden | Voorgestelde verbetering |
|---|-----------|-------------|-----------|--------------------------|
| 8.1 | **parseOnlineIdFromResponse** | Dezelfde response-parsing logica op twee plaatsen | [`TellingDataProcessor`](app/src/main/java/com/yvesds/vt5/features/telling/TellingDataProcessor.kt:26) en [`TellingUploadCore`](app/src/main/java/com/yvesds/vt5/features/telling/TellingUploadCore.kt:271) | Centraliseer in één utility/extensiefunctie |
| 8.2 | **SAF directory management** | Het zoeken/aanmaken van VT5/counts/exports mappen wordt **minstens 3x** gedupliceerd | `DataUploader`, `TellingEnvelopePersistence`, `TellingBackupManager` | Maak één `SaFStorageHelper`-utility die alle folder-operaties centraliseert |
| 8.3 | **JSON configuratie** | `Json { ignoreUnknownKeys = true; encodeDefaults = true }` wordt op meerdere plaatsen geïnitialiseerd | `VT5App.json`, `TrektellenApi.json`, `WeatherManager.json`, `TellingEnvelopePersistence.PRETTY_JSON` | Hergebruik `VT5App.json` overal; voeg één pretty-print variant toe |
| 8.4 | **Color spinner adapter** | [`setupColorSpinners()`](app/src/main/java/com/yvesds/vt5/hoofd/InstellingenScherm.kt:234) en [`setupLogColorSpinner()`](app/src/main/java/com/yvesds/vt5/hoofd/InstellingenScherm.kt:419) hebben bijna identieke adapter code | ~100 lijnen bijna-identieke code | Maak één herbruikbare `ColorOptionAdapter` class |

---

## 9. Overige / Quick Wins

| # | Omschrijving | Bestand | Waarom |
|---|-------------|---------|--------|
| 9.1 | `@Synchronized` op `nextTellingId()` werkt niet met coroutines | [`VT5App.nextTellingId()`](app/src/main/java/com/yvesds/vt5/VT5App.kt:177) | `@Synchronized` is Java-monitor based; werkt enkel op dezelfde thread. In coroutine context is een `Mutex` nodig |
| 9.2 | `compose = false` terwijl libs.versions.toml Compose bevat | [`app/build.gradle.kts`](app/src/main/java/com/yvesds/vt5/build.gradle.kts:35) en [`libs.versions.toml`](gradle/libs.versions.toml) | Opschonen van ongebruikte dependencies |
| 9.3 | `android:launchMode="singleTop"` op TellingScherm | [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml:88) | Correct, maar de `onNewIntent()` handler moet alle intent-extras correct verwerken |
| 9.4 | Geen unit tests of instrumented tests | Hele codebase | Tests zijn expliciet uitgeschakeld in build.gradle.kts — overweeg op zijn minst enkele kritische paden te testen |

---

## Prioriteitsscore

| Prioriteit | Categorieën | Reden |
|-----------|-------------|-------|
| **🔴 Hoog** | 1.1 (R8), 6.1 (alpha crypto), 7.1 (coroutine leaks), 5.7 (EncryptedSharedPrefs) | Directe impact op veiligheid, stabiliteit en APK grootte |
| **🟡 Medium** | 2.1 (God-class), 3.2 (File I/O), 5.1 (DataStore), 5.6 (WorkManager), 7.2 (backoff), 8.1-8.4 (duplicatie) | Merkbare verbetering in onderhoudbaarheid en gebruikerservaring |
| **🟢 Laag** | 2.2 (DI), 2.4 (Navigation), 5.5 (Compose), 1.3 (Baseline Profiles) | Grote refactors, planning vereist; best in aparte fases |

---

## Conclusie

De codebase toont een **solide, functionele app** die duidelijk is geëvolueerd van een eenvoudig prototype naar een volwaardige productie-app. De grootste pijnpunten zijn:

1. **`TellingScherm.kt` (3123 lijnen)** — architecturale schuld door opeenvolgende feature-toevoegingen
2. **Geen R8/proguard** — de APK is onnodig groot en niet geoptimaliseerd
3. **Alpha-versie van security-crypto** — een beveiligingsrisico
4. **Dubbele SAF-operaties** — meerdere componenten strijden om dezelfde bestanden
5. **SharedPreferences als database** — schreeuwt om migratie naar DataStore of Room
6. **Ontbrekende exponential backoff** in netwerk-herverbindingen

Een gefaseerde aanpak wordt aanbevolen: eerst de quick wins (R8, security upgrade, backoff), daarna de grotere architecturale verbeteringen (DI, DataStore, opsplitsen TellingScherm).
