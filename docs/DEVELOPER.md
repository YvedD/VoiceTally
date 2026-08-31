# VoiceTally — Projectoverzicht & Developer Quickstart

Dit bestand bevat een uitgebreide gebruikershandleiding. Onderstaande sectie is toegevoegd als snelle referentie voor ontwikkelaars die het project willen bouwen, testen of bijdragen.

Belangrijkste onderdelen:
- Android app: module `app/` (package `com.yvesds.vt5`)
- BirdNET-integratie: referentie Go-bestanden in root (birdnet-*.go), zie `birdnet-go-guide.md`
- Documentatie: `API-Documentatie.md`, `MASTER-CLIENTS-README.md`, `birdnet-README.md`

Kort — Developer Quickstart (Windows PowerShell)
1) Voorbereiding:
   - Installeer JDK 17+, Android SDK (API 33+), en Android Studio of build-tools.
   - Zorg dat `ANDROID_HOME` en `JAVA_HOME` correct zijn ingesteld.
2) Android build (debug):
```powershell
cd 'C:\Eigen bestanden Yves\Programeren\Android\VoiceTally'
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```
3) Android build (release, signed):
```powershell
.\gradlew.bat :app:assembleRelease --no-daemon --console=plain
# Keystore: VoiceTally.jks of voicetallykey.jks (in project root) — signing config staat in app/build.gradle.kts
```
4) Clean build:
```powershell
.\gradlew.bat clean --no-daemon
```
5) BirdNET / Go bestanden:
- De `birdnet-*.go` bestanden in de repo zijn referentie-implementaties voor BirdNET-Go. In deze repo is geen `package main` aanwezig; om BirdNET-Go te bouwen volg `birdnet-go-guide.md` of de upstream BirdNET-Go repo (Docker/Make).
6) Belangrijke paden:
- Android code: `app/src/main/java/` en `app/src/main/res/`
- APK output: `app/build/outputs/apk/`
- Keystores: `VoiceTally.jks`, `voicetallykey.jks`
- Server data: `serverdata-samples/`
7) Waar documentatie te vinden:
- Gebruikershandleiding: `README.md`
- API: `API-Documentatie.md`
- BirdNET: `birdnet-README.md`, `birdnet-go-guide.md`

Voor meer details en uitgebreide gebruikersinstructies: zie `README.md` (gebruikershandleiding) of andere documentatiebestanden in de repository.

