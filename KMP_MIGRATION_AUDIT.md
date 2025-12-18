# VoiceTally - Kotlin Multiplatform Migration Audit
## Datum: 18 december 2025

---

## Executive Summary

Deze audit analyseert de VoiceTally (VT5) Android applicatie en geeft een gedetailleerd overzicht van wat er nodig is om deze om te bouwen naar een Kotlin Multiplatform (KMP) applicatie met iOS ondersteuning.

### Huidige Status
- **Type**: Native Android applicatie
- **Taal**: Kotlin
- **Min SDK**: Android 13 (API 33)
- **Target SDK**: Android 35
- **Aantal Kotlin bestanden**: ~105
- **Build systeem**: Gradle (Kotlin DSL)
- **Architectuur**: Feature-based modular structuur

---

## 1. Analyse van de Huidige Architectuur

### 1.1 Projectstructuur
```
VT5/
├── app/
│   ├── src/main/java/com/yvesds/vt5/
│   │   ├── core/                  # Core functionaliteit
│   │   ├── features/              # Feature modules
│   │   ├── net/                   # Netwerk laag
│   │   ├── network/               # Data upload
│   │   ├── utils/                 # Utilities
│   │   ├── hoofd/                 # Hoofd activiteiten
│   │   ├── splash/                # Splash screen
│   │   └── VT5App.kt             # Application class
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

### 1.2 Feature Modules Geïdentificeerd
1. **Opstart** (`features/opstart`) - Installatie en configuratie
2. **Metadata** (`features/metadata`) - Telpost gegevens
3. **Soort** (`features/soort`) - Soorten selectie
4. **Telling** (`features/telling`) - Hoofd tel functionaliteit
5. **Speech** (`features/speech`) - Spraakherkenning
6. **Alias** (`features/alias`) - Alias management
7. **Annotation** (`features/annotation`) - Waarneming annotaties
8. **Recent** (`features/recent`) - Recente soorten
9. **Serverdata** (`features/serverdata`) - Server synchronisatie

### 1.3 Core Componenten
1. **App** (`core/app`) - Application lifecycle, alarms
2. **UI** (`core/ui`) - Shared UI componenten
3. **Secure** (`core/secure`) - Credentials opslag
4. **Opslag** (`core/opslag`) - Storage Access Framework (SAF)

### 1.4 Netwerk & API
- `TrektellenApi.kt` - Hoofdcommunicatie met trektellen.nl
- `StartTellingApi.kt` - Telling start API
- `DataUploader.kt` - Data synchronisatie
- Gebruikt: `OkHttp3` voor HTTP communicatie

### 1.5 Dependencies Analyse
```kotlin
// Huidige Android-specifieke dependencies:
- androidx.core:core-ktx
- androidx.appcompat:appcompat
- androidx.activity:activity-ktx
- com.google.android.material:material
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.recyclerview:recyclerview
- com.google.android.flexbox:flexbox
- androidx.documentfile:documentfile
- androidx.security:security-crypto
- kotlinx-serialization-json (✓ KMP compatible)
- kotlinx-serialization-cbor (✓ KMP compatible)
- commons-codec:commons-codec
- org.apache.commons:commons-text
- com.squareup.okhttp3:okhttp
- kotlinx-coroutines-android (✓ KMP compatible)
- androidx.work:work-runtime-ktx
- androidx.media:media
```

---

## 2. Platform-Afhankelijkheden Analyse

### 2.1 Android-Specifieke Functionaliteit

#### **KRITISCH** - Moeilijk te migreren:
1. **Speech Recognition** 
   - Gebruikt: `android.speech.SpeechRecognizer`
   - Impact: HOOG - Kernfunctionaliteit van de app
   - iOS alternatief: `SFSpeechRecognizer` (Swift/iOS native)
   - Oplossing: Expect/Actual pattern

2. **Storage Access Framework (SAF)**
   - Gebruikt: `androidx.documentfile.DocumentFile`
   - Impact: HOOG - Alle file I/O
   - iOS alternatief: iOS FileManager
   - Oplossing: Expect/Actual pattern voor file operations

3. **Android Activities & Lifecycle**
   - Gebruikt: `AppCompatActivity`, lifecycle components
   - Impact: HOOG - Hele UI laag
   - iOS: Volledig nieuwe UI nodig (SwiftUI/UIKit)

4. **Permissions System**
   - Gebruikt: Android runtime permissions
   - Impact: GEMIDDELD
   - iOS: Eigen permission systeem
   - Oplossing: Expect/Actual pattern

5. **Alarms & Background Work**
   - Gebruikt: `AlarmManager`, `WorkManager`
   - Impact: GEMIDDELD
   - iOS: UNUserNotificationCenter
   - Oplossing: Expect/Actual pattern

6. **Media & Audio**
   - Gebruikt: `MediaPlayer`, `AudioManager`
   - Impact: LAAG - Alleen voor alarm sounds
   - iOS: AVFoundation

#### **GEMIDDELD** - Redelijk te migreren:
1. **Netwerk (OkHttp)**
   - Impact: GEMIDDELD
   - Oplossing: Migreren naar Ktor (KMP HTTP client)

2. **Encrypted Storage**
   - Gebruikt: `androidx.security.security-crypto`
   - Impact: GEMIDDELD
   - iOS: Keychain
   - Oplossing: Expect/Actual pattern

3. **Location Services**
   - Gebruikt: Android Location API
   - Impact: GEMIDDELD
   - iOS: CoreLocation
   - Oplossing: Expect/Actual pattern

#### **LAAG** - Eenvoudig te migreren:
1. **Data Models & Serialization**
   - Gebruikt: kotlinx.serialization
   - Impact: LAAG - Al KMP compatible!
   - Oplossing: Direct naar shared module

2. **Business Logic**
   - Pure Kotlin code zonder Android dependencies
   - Impact: LAAG
   - Oplossing: Direct naar shared module

3. **Utilities**
   - Meeste utility functies zijn platform-onafhankelijk
   - Impact: LAAG

### 2.2 Schatting Code Verdeling
```
┌─────────────────────────────────────────┐
│ Code Categorieën                        │
├─────────────────────────────────────────┤
│ UI Laag (Android Activities/Views) 40%  │ → Blijft Android-specifiek
│ Business Logic                     25%  │ → Naar shared module
│ Data Models                        10%  │ → Naar shared module
│ Netwerk & API                      10%  │ → Naar shared module (Ktor)
│ Platform Services                  10%  │ → Expect/Actual
│ Utilities                           5%  │ → Grotendeels shared
└─────────────────────────────────────────┘

Totaal herbruikbaar voor iOS: ~50-60%
```

---

## 3. Migratieplan naar Kotlin Multiplatform

### 3.1 Aanbevolen Structuur
```
VoiceTally/
├── shared/                          # KMP Shared Module
│   ├── src/
│   │   ├── commonMain/kotlin/      # Platform-onafhankelijke code
│   │   │   ├── domain/             # Business logic
│   │   │   ├── data/               # Data models
│   │   │   ├── network/            # API clients (Ktor)
│   │   │   └── utils/              # Shared utilities
│   │   ├── androidMain/kotlin/     # Android implementaties
│   │   │   ├── platform/           # Platform-specifiek
│   │   │   └── actual/             # Actual implementaties
│   │   └── iosMain/kotlin/         # iOS implementaties
│   │       ├── platform/           # Platform-specifiek
│   │       └── actual/             # Actual implementaties
│   └── build.gradle.kts
│
├── androidApp/                      # Android App Module
│   ├── src/main/
│   │   ├── java/                   # Android UI laag
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── iosApp/                          # iOS App Module
│   ├── iosApp/                     # Swift/SwiftUI code
│   ├── Podfile                     # CocoaPods
│   └── iosApp.xcodeproj/
│
├── build.gradle.kts
└── settings.gradle.kts
```

### 3.2 Fasering

#### **Fase 1: Setup & Configuratie** (Geschat: 1-2 dagen)
- [ ] Kotlin Multiplatform plugin toevoegen aan project
- [ ] Shared module aanmaken
- [ ] Build configuratie aanpassen
- [ ] Version catalog updaten met KMP dependencies
- [ ] iOS targets configureren (iosArm64, iosX64, iosSimulatorArm64)
- [ ] CocoaPods integratie setup

#### **Fase 2: Data Laag Migratie** (Geschat: 2-3 dagen)
- [ ] Data models migreren naar `commonMain`
- [ ] Types.kt (net/Types.kt) migreren
- [ ] WeatherResponse.kt migreren
- [ ] Server response models migreren
- [ ] Serialization configuratie

#### **Fase 3: Netwerk Laag Migratie** (Geschat: 3-4 dagen)
- [ ] OkHttp vervangen door Ktor Client
- [ ] TrektellenApi.kt herstructureren voor KMP
- [ ] StartTellingApi.kt herstructureren voor KMP
- [ ] DataUploader.kt logica naar shared
- [ ] HTTP client configuratie (Android: OkHttp engine, iOS: Darwin engine)

#### **Fase 4: Business Logic Migratie** (Geschat: 5-7 dagen)
- [ ] AliasMatcher.kt naar shared
- [ ] AliasSpeechParser.kt naar shared (zonder Android Speech API)
- [ ] SeizoenUtils.kt naar shared
- [ ] WeatherManager.kt logica naar shared
- [ ] Annotation management naar shared
- [ ] Recent species logica naar shared

#### **Fase 5: Platform Interfaces (Expect/Actual)** (Geschat: 7-10 dagen)

##### 5.1 Storage Interface
```kotlin
// commonMain
expect class FileStorage {
    suspend fun readFile(path: String): ByteArray
    suspend fun writeFile(path: String, data: ByteArray)
    suspend fun listFiles(directory: String): List<String>
    suspend fun deleteFile(path: String)
}

// androidMain - SAF implementatie
// iosMain - FileManager implementatie
```

##### 5.2 Speech Recognition Interface
```kotlin
// commonMain
expect class SpeechRecognizer {
    fun startListening(onResult: (String) -> Unit)
    fun stopListening()
    fun isAvailable(): Boolean
}

// androidMain - Android SpeechRecognizer
// iosMain - SFSpeechRecognizer wrapper
```

##### 5.3 Secure Storage Interface
```kotlin
// commonMain
expect class SecureStorage {
    suspend fun saveCredentials(username: String, password: String)
    suspend fun getCredentials(): Pair<String, String>?
    suspend fun clearCredentials()
}

// androidMain - EncryptedSharedPreferences
// iosMain - Keychain
```

##### 5.4 Location Interface
```kotlin
// commonMain
expect class LocationProvider {
    suspend fun getCurrentLocation(): Location?
    fun requestPermission()
}

// androidMain - FusedLocationProvider
// iosMain - CLLocationManager wrapper
```

##### 5.5 Permissions Interface
```kotlin
// commonMain
expect class PermissionManager {
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun requestLocationPermission(): Boolean
    suspend fun checkPermission(permission: Permission): Boolean
}
```

#### **Fase 6: iOS App Ontwikkeling** (Geschat: 14-21 dagen)
- [ ] Xcode project setup
- [ ] SwiftUI views ontwikkelen (equivalenten van Android Activities)
- [ ] Navigation flow implementeren
- [ ] Platform-specifieke UI componenten
- [ ] iOS-specifieke features (speech, storage, etc.)
- [ ] App icons en assets
- [ ] iOS permissions configuratie (Info.plist)

#### **Fase 7: Testing & Refinement** (Geschat: 5-7 dagen)
- [ ] Android build testen
- [ ] iOS simulator tests
- [ ] iOS fysiek apparaat tests
- [ ] Cross-platform bug fixes
- [ ] Performance optimalisatie
- [ ] UI/UX aanpassingen iOS

#### **Fase 8: Documentatie** (Geschat: 2-3 dagen)
- [ ] README updaten
- [ ] iOS gebruikershandleiding
- [ ] Build instructies voor iOS
- [ ] Developer documentatie
- [ ] Migration guide

### 3.3 Totaal Geschatte Tijd
- **Minimaal**: 6-8 weken (fulltime)
- **Realistisch**: 10-14 weken (met refinement en testing)
- **Met team van 2-3**: 4-6 weken

---

## 4. Dependency Migratie

### 4.1 Te Vervangen Dependencies

| Android Dependency | KMP Alternatief | Complexiteit |
|-------------------|-----------------|--------------|
| OkHttp | Ktor Client | Gemiddeld |
| Android Security Crypto | Expect/Actual | Hoog |
| DocumentFile (SAF) | Expect/Actual | Hoog |
| Android Location | Expect/Actual | Gemiddeld |
| Commons Text | Pure Kotlin impl | Laag |
| Commons Codec | Pure Kotlin impl | Laag |

### 4.2 Nieuwe KMP Dependencies

```kotlin
// In shared/build.gradle.kts
commonMain.dependencies {
    // Coroutines (al aanwezig, KMP compatible)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    
    // Serialization (al aanwezig, KMP compatible)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.7.3")
    
    // Nieuwe KMP dependencies
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    
    // Optioneel: Settings (multiplatform key-value storage)
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
}

androidMain.dependencies {
    implementation("io.ktor:ktor-client-android:3.0.1")
}

iosMain.dependencies {
    implementation("io.ktor:ktor-client-darwin:3.0.1")
}
```

---

## 5. Risico's & Uitdagingen

### 5.1 Hoog Risico
1. **Speech Recognition Verschillen**
   - Android en iOS hebben verschillende capabilities
   - Spraakherkenning kwaliteit kan verschillen
   - Offline mode mogelijk anders

2. **File Storage Complexiteit**
   - SAF is zeer Android-specifiek
   - iOS heeft beperktere file access
   - Migration van bestaande data

3. **UI Herontwikkeling**
   - Volledige iOS UI moet opnieuw gebouwd worden
   - SwiftUI vs Android Views zijn fundamenteel verschillend
   - Design consistency tussen platforms

### 5.2 Gemiddeld Risico
1. **Performance**
   - KMP overhead (minimaal, maar bestaat)
   - Cross-platform serialization
   - Memory management op iOS

2. **Testing Complexiteit**
   - Dubbele test suites nodig
   - Platform-specifieke bugs
   - CI/CD voor beide platforms

### 5.3 Laag Risico
1. **Build Configuratie**
   - Wel complex, maar gedocumenteerd
   - Gradle/CocoaPods integratie is stabiel

2. **Dependency Conflicts**
   - KMP ecosysteem is volwassen
   - Meeste dependencies hebben KMP versies

---

## 6. Alternatieve Benaderingen

### 6.1 Optie A: Volledige KMP Migratie (Aanbevolen)
**Voor**: Maximum code reuse, native performance, beste UX
**Tegen**: Meeste werk, iOS UI moet volledig nieuw
**Geschat**: 10-14 weken

### 6.2 Optie B: Geleidelijke Migratie
**Voor**: Minder risico, stapsgewijs
**Tegen**: Langere totale tijd
**Geschat**: 14-18 weken
**Aanpak**:
1. Start met shared data models
2. Dan shared business logic
3. Dan platform interfaces
4. Laatst iOS UI

### 6.3 Optie C: Hybride Approach (Flutter/React Native)
**Voor**: Snellere iOS release, één UI codebase
**Tegen**: Niet Kotlin, performance trade-offs, hele app herschrijven
**Geschat**: 12-16 weken
**Note**: Dit verlaat Kotlin ecosysteem

### 6.4 Optie D: iOS Native App met Backend API
**Voor**: Makkelijkste voor verschillende teams
**Tegen**: Dubbele codebases, meer onderhoud
**Geschat**: 8-10 weken (alleen iOS)
**Note**: Geen code reuse

---

## 7. Aanbevelingen

### 7.1 Primaire Aanbeveling: Volledige KMP Migratie (Optie A)

**Waarom?**
1. VoiceTally is al in Kotlin - maximale code reuse mogelijk
2. Business logic is goed gescheiden van UI
3. KMP is volwassen en production-ready
4. Native performance op beide platforms
5. Toekomstbestendig (bijvoorbeeld Android Wear, Desktop later)

**Succesfactoren:**
- ~50-60% code reuse tussen platforms
- Shared business logic = minder bugs
- Type-safe APIs tussen platforms
- Native UI op beide platforms

### 7.2 Prioriteiten voor Start

**Week 1-2: Foundation**
1. Project setup & shared module
2. Data models naar shared
3. Build configuratie werkend krijgen

**Week 3-4: Network Layer**
1. Ktor integratie
2. API clients naar shared
3. Testing van network layer

**Week 5-6: Platform Interfaces**
1. Storage expect/actual
2. Speech expect/actual
3. Android implementaties

**Week 7-10: iOS Development**
1. iOS project setup
2. SwiftUI screens
3. iOS platform implementaties

**Week 11-14: Polish & Release**
1. Testing beide platforms
2. Bug fixes
3. Performance tuning
4. Documentation
5. App Store prep

### 7.3 Team Samenstelling (Ideaal)
- 1-2 Kotlin/Android developers (shared + Android)
- 1 iOS developer (Swift/SwiftUI + iOS platform impl)
- 1 QA tester
- (Optioneel) 1 Designer voor iOS UI/UX

---

## 8. Kosten-Baten Analyse

### 8.1 Investering
- **Ontwikkeltijd**: 10-14 weken
- **Team kosten**: Afhankelijk van team size
- **Tooling**: Xcode (gratis), Apple Developer Account (€99/jaar)
- **Leertraject**: KMP & iOS development kennis

### 8.2 Voordelen
- **Code reuse**: 50-60% minder code
- **Onderhoud**: Eenmalige bug fixes voor beide platforms
- **Feature parity**: Automatisch synchronized features
- **Performance**: Native performance beide platforms
- **Toekomst**: Makkelijk uitbreidbaar naar andere platforms

### 8.3 Return on Investment
- Break-even na ~6-12 maanden (door gedeeld onderhoud)
- Elke nieuwe feature kost ~50% minder werk
- Bug fixes kost ~50% minder tijd
- Hogere code kwaliteit door gedeelde tests

---

## 9. Belangrijke Overwegingen

### 9.1 Spraakherkenning op iOS
iOS spraakherkenning (SFSpeechRecognizer) is **uitstekend**, maar:
- Vereist user permission
- Offline mode beschikbaar maar beperkt
- Nederlandse taal goed ondersteund
- Real-time transcriptie mogelijk

**Implementatie strategie:**
```kotlin
// commonMain
expect class SpeechRecognizer {
    fun startListening(language: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit)
}

// iosMain - Swift wrapper nodig
actual class SpeechRecognizer {
    // Bridge naar SFSpeechRecognizer
}
```

### 9.2 Storage op iOS
iOS heeft **geen** SAF equivalent, maar:
- App sandbox met Documents directory
- iCloud sync mogelijk
- Keychain voor credentials

**Migratiepad:**
- SAF storage → Generic file operations
- Expect/Actual pattern voor platform-specifieke paths
- Data format blijft gelijk (JSON/CBOR)

### 9.3 Permissions op iOS
iOS permissions via Info.plist:
```xml
<key>NSMicrophoneUsageDescription</key>
<string>VoiceTally gebruikt de microfoon voor spraakherkenning van vogelnamen</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>VoiceTally gebruikt locatie voor weergegevens</string>

<key>NSSpeechRecognitionUsageDescription</key>
<string>VoiceTally gebruikt spraakherkenning voor snelle invoer</string>
```

---

## 10. Conclusie

De migratie van VoiceTally naar Kotlin Multiplatform is **haalbaar en aanbevolen**. 

### Kernpunten:
✅ **Haalbaar**: Code is goed gestructureerd, moderne Kotlin
✅ **Waardevol**: 50-60% code reuse
✅ **Toekomstbestendig**: KMP is mature en groeiend
✅ **Native**: Beste UX op beide platforms

⚠️ **Let op**:
- iOS UI moet volledig nieuw (SwiftUI)
- Speech & Storage need platform-specific implementations
- Investering: 10-14 weken development

### Next Steps:
1. Goedkeuring voor KMP approach
2. Team samenstellen (vooral iOS developer)
3. Start met Fase 1: Project setup
4. Incrementele migratie per feature

---

## Appendix A: Bronnen

- [Kotlin Multiplatform Documentatie](https://kotlinlang.org/docs/multiplatform.html)
- [Ktor Client](https://ktor.io/docs/getting-started-ktor-client.html)
- [KMP Samples](https://github.com/KaterinaPetrova/kmm-icerock-sample)
- [iOS Speech Recognition](https://developer.apple.com/documentation/speech/sfspeechrecognizer)
- [CocoaPods Setup](https://kotlinlang.org/docs/native-cocoapods.html)

---

**Document versie**: 1.0  
**Auteur**: GitHub Copilot  
**Datum**: 18 december 2025
