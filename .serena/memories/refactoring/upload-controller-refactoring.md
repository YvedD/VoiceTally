# UploadController Refactoring

## Doel
De `UploadController` refactoren zodat deze gebruikmaakt van de centrale `TellingUploadCore` voor de daadwerkelijke uploadlogica, in plaats van deze zelf te implementeren.

## Wijzigingen

### 1. `UploadController.kt` - Toevoegen `TellingUploadCore` dependency
- `TellingUploadCore` geïnjecteerd via constructor
- Nieuwe `upload()` methode die `TellingUploadCore.uploadPrepared()` aanroept
- Oude `upload()` methode hernoemd naar `uploadDirect()` voor directe HTTP-aanroepen
- `uploadDirect()` is `private` gemaakt (alleen intern gebruikt)

### 2. `TellingUploadCore.kt` - Nieuwe `prepareEnvelopeForUpload()` methode
- Nieuwe publieke methode die een envelope voorbereidt voor upload
- Optioneel gebruik van opgeslagen `onlineId` via `useStoredOnlineIdWhenBlank`
- Optioneel gebruik van opgegeven `now` datum

### 3. `TellingAfrondHandler.kt` - Gebruikt al `TellingUploadCore`
- `TellingAfrondHandler` gebruikte al `TellingUploadCore.uploadPrepared()` - geen wijzigingen nodig
- Wel gebruikt het nu `uploadCore.prepareEnvelopeForUpload()` voor envelope voorbereiding

### 4. `TellingViewModel.kt` - Gebruikt `UploadController.upload()`
- Roept `uploadController.upload()` aan, die nu via `TellingUploadCore` gaat
- Geen wijzigingen nodig

## Testresultaten
- Project heeft een bestaande circulaire afhankelijkheid tussen `:app` en `:feature:telling` modules
- Hierdoor kunnen tests niet worden uitgevoerd
- Dit is een bestaand probleem, niet veroorzaakt door deze refactoring
