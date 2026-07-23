# Walkthrough: Fase 1 - AI Instellingen & DataStore Integratie

Ik heb de eerste fase van de AI-roadmap voltooid. Deze fase richtte zich op het geven van controle aan de gebruiker over de AI-functionaliteit en het persistent opslaan van deze keuze.

## Wat is er veranderd?

### 1. Gebruikersinterface (UI)
In het **InstellingenScherm** is een nieuwe sectie "AI-functionaliteit" toegevoegd:
- **AI-meldingen inschakelen:** Een checkbox waarmee de gebruiker de AI-functies kan activeren of deactiveren.
- **Beschrijving:** Een korte uitleg over wat er gebeurt als de functie is ingeschakeld (lokaal aanmaken van modellen).
- **Map Selectie:** De knop voor het selecteren van de AI-model map is nu netjes geïntegreerd in deze sectie en gebruikt gelokaliseerde strings.

### 2. Gegevensopslag (DataStore)
- De status van de AI-schakelaar wordt nu veilig en persistent opgeslagen in de `AppDataStore` onder de sleutel `ai_enabled`.
- De standaardwaarde is `false`, wat betekent dat AI standaard uit staat (zoals gevraagd).

### 3. AI Beheer (Lifecycle)
- De `AiManager` is aangepast om de instelling te respecteren:
    - Bij het opstarten van de app (`init`) wordt alleen een nightly update ingepland als AI is ingeschakeld. Zo niet, dan wordt een eventuele actieve worker geannuleerd.
    - Handmatige updates via `requestManualUpdate` worden geblokkeerd als de instelling uit staat.

## Technische Details

- **Bestanden gewijzigd:**
    - [AppDataStore.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/core/opslag/AppDataStore.kt): Toegevoegd `isAiEnabled` en `setAiEnabled`.
    - [strings.xml](file:///C:/AndroidApps/VoiceTally/app/src/main/res/values/strings.xml): Toegevoegd gelokaliseerde strings voor de AI-sectie.
    - [scherm_instellingen.xml](file:///C:/AndroidApps/VoiceTally/app/src/main/res/layout/scherm_instellingen.xml): UI-layout bijgewerkt met de AI-sectie.
    - [InstellingenScherm.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/hoofd/InstellingenScherm.kt): Logica voor de checkbox en map-selectie strings toegevoegd.
    - [AiManager.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/AiManager.kt): Integratie van de DataStore-check in de AI-lifecycle.

## Verificatie
- De code is gecontroleerd met `analyze_file` en vertoont geen fouten.
- De logica in `AiManager` zorgt ervoor dat er geen achtergrondtaken draaien als de gebruiker dit niet expliciet heeft aangezet.

---
**Volgende stap:** Onderzoek naar Fase 2 (On-device Model Generatie) zoals beschreven in de roadmap.
