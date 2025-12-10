# Build Fix: Kotlin Daemon Crash Resolution

## Problem Summary
De build faalde met een Kotlin daemon crash error bij het compileren van de branch 'main-pre-release-test'. De root cause was dat de Android Gradle Plugin (AGP) versie 8.10.1 niet bestaat in de Maven repositories.

## Changes Made

### 1. gradle/libs.versions.toml
**Changed from:** `agp = "8.10.1"`  
**Changed to:** `agp = "8.5.0"`

**Reason:** 
- AGP versie 8.10.1 bestaat niet in de public Maven repositories
- Kotlin 2.0.21 (gebruikt in dit project) is compatibel met AGP versies 7.4.2 tot en met 8.5.0
- AGP 8.5.0 is de maximum ondersteunde versie voor Kotlin 2.0.21
- Gebruik van AGP 8.7+ vereist een upgrade naar Kotlin 2.1.x

### 2. settings.gradle.kts
**Changed:** Repository configuratie om expliciet `maven.google.com` te gebruiken

**Before:**
```kotlin
repositories {
    google()
    mavenCentral()
}
```

**After:**
```kotlin
repositories {
    maven { url = uri("https://maven.google.com") }
    mavenCentral()
}
```

**Reason:**
- Expliciete Maven URL voor betere compatibiliteit
- Voorkomt potentiële problemen met repository resolution

## Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Kotlin | 2.0.21 | Current |
| AGP | 8.5.0 | Maximum for Kotlin 2.0.21 |
| Gradle | 8.11.1 | Current (Kotlin 2.0.21 supports 6.8.3 - 8.8, may have warnings) |
| Min SDK | 33 | Target SDK: 35 |
| JVM Target | 17 | |

**Note:** Gradle 8.11.1 is newer than the officially tested range for Kotlin 2.0.21 (6.8.3 - 8.8). Consider downgrading to Gradle 8.6 or 8.7 for maximum stability, though 8.11.1 should generally work.

## How to Verify

### Option 1: Clean Build
```bash
./gradlew clean assembleDebug
```

### Option 2: With Daemon
```bash
./gradlew clean
./gradlew assembleDebug
```

### Option 3: Force Refresh Dependencies
```bash
./gradlew clean assembleDebug --refresh-dependencies
```

## Expected Outcome
De build zou nu succesvol moeten compileren zonder Kotlin daemon crashes. De error message:
```
The daemon has terminated unexpectedly on startup attempt #1 with error code: 0
```
Zou niet meer moeten voorkomen.

## Network Requirements
**Important:** Deze fix vereist netwerk toegang tot:
- https://maven.google.com (of https://dl.google.com)
- https://repo.maven.apache.org
- https://plugins.gradle.org

Als je nog steeds build errors krijgt, controleer of je netwerk toegang heeft tot deze repositories.

## Future Upgrades

### To upgrade to AGP 8.7+:
1. Upgrade Kotlin naar minimaal 2.1.0:
   ```toml
   kotlin = "2.1.0"
   ```
2. Then upgrade AGP:
   ```toml
   agp = "8.7.0"  # or higher
   ```

### To upgrade Kotlin while keeping AGP 8.5.0:
- You can upgrade Kotlin to any 2.0.x version (e.g., 2.0.21, 2.0.20, etc.)
- AGP 8.5.0 supports all Kotlin 2.0.x versions

### Optional: Downgrade Gradle for maximum stability:
Als je warnings of problemen ervaart met Gradle 8.11.1:
1. Update `gradle/wrapper/gradle-wrapper.properties`:
   ```properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
   ```
2. Run: `./gradlew wrapper --gradle-version=8.6`

## Additional Notes
- De oorspronkelijke AGP versie (8.10.1) bestaat niet in de Maven repositories
- De comment in het bestand vermeldde al dat 8.10.1 niet beschikbaar was, maar suggereerde 8.3.0
- We gebruiken nu 8.5.0 voor de beste compatibiliteit met Kotlin 2.0.21

### Gradle 8.11.1 Compatibility Note
Het project gebruikt momenteel Gradle 8.11.1, wat nieuwer is dan de officieel geteste range voor Kotlin 2.0.21 (6.8.3 - 8.8). Dit kan leiden tot:
- Deprecation warnings in de build output
- Potentiële incompatibiliteiten bij gebruik van Kotlin Multiplatform features
- Onverwacht gedrag in edge cases

**Aanbeveling:** Als je problemen ondervindt, overweeg dan een downgrade naar Gradle 8.6 of 8.7 (zie "Optional: Downgrade Gradle" sectie hierboven). Voor reguliere Android development zonder Multiplatform zou Gradle 8.11.1 echter moeten werken.
