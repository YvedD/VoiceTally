# Plan: Fase 2 - On-Device Model Generatie

In deze fase implementeren we de logica om een AI-model en labels-bestand direct op het toestel te genereren op basis van de Room DB data.

## Gebruikersbeoordeling Vereist

- **Basismodel:** Ik stel voor om een klein, universeel "base model" (MobileNet-achtig maar voor tabel-data) in de assets van de app te plaatsen. Dit model wordt vervolgens lokaal "gefinetuned". Ben je hiermee akkoord?
- **Training Tijdstip:** De training wordt standaard ingepland via `WorkManager` met de voorwaarden: *toestel aan de lader* en *toestel niet in gebruik*.

## Voorgestelde Wijzigingen

### Database: Soortnamen ophalen

#### [TellingDao.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/core/database/dao/TellingDao.kt)
- Toevoegen van `getAllUniqueSpeciesIds()` om de lijst met soorten te krijgen die daadwerkelijk in de database voorkomen.

---

### AI: Data Preparatie

#### [TrainingDataPreparer.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/TrainingDataPreparer.kt)
- Implementeren van `generateLabelsJson()`: Dit maakt het `training_model.labels.json` bestand aan met de mapping van soorten naar model-indices.

---

### AI: Training Logica

#### [Trainer.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/Trainer.kt)
- Vervangen van de "stubs" door echte training-logica.
- Gebruik van de TensorFlow Lite Java API voor on-device training.
- Laden van het basismodel uit assets en updaten van de gewichten op basis van de geëxporteerde CSV.

---

### AI: Workflow

#### [AiUpdateWorker.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/AiUpdateWorker.kt)
- De worker aanpassen zodat deze achtereenvolgens:
    1. De CSV exporteert.
    2. De `labels.json` genereert.
    3. De `Trainer` aanroept voor de eigenlijke model-update.

---

## Verificatieplan

### Geautomatiseerde Tests
- Unit test voor `generateLabelsJson` om te controleren of de JSON correct wordt opgebouwd.
- Verificatie van de `WorkManager` constraints via de `ADB` shell.

### Handmatige Verificatie
- Start een handmatige AI-update via het instellingenscherm (als het toestel aan de lader ligt).
- Controleer via de SAF-map of `training_model.tflite` en `training_model.labels.json` zijn aangemaakt/bijgewerkt.
- Bekijk de logs in Logcat om de voortgang van de training te volgen.
