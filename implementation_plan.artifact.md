# Implementatieplan: BSI 4.0 "High-Precision Dynamic Mining"

Dit plan beschrijft de upgrade van het BSI-subsysteem naar versie 4.0, met focus op meteorologische "Weather Twins", Temporal Blueprinting voor dag-totalen en verscherpte BoI (Birds-of-Interest) logica.

## User Review Required

> [!IMPORTANT]
> **Data Integriteit Waarschuwing**: We gaan de manier waarop data uit de Room DB wordt opgehaald fundamenteel wijzigen (van statische profielen naar dynamische mining). Dit vereist een éénmalige hertraining van de BSI na de update om de nieuwe indexen en profielen te activeren.
>
> **Veiligheidsgarantie**: Er zal géén bestaande code worden verwijderd boven de limiet van 100 regels. Alle wijzigingen zijn additief of chirurgische vervangingen van specifieke berekeningsformules.

## Proposed Changes

### 1. Database Laag (Room DAO)
We voegen precisie-queries toe die het hart vormen van de nieuwe mining-motor.

#### [MODIFY] [TellingDao.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/core/database/dao/TellingDao.kt)
- Toevoegen van `getWeatherTwins`: Zoekt uren in de historie met windrichting +/- 5.25° binnen het tijdvenster.
- Toevoegen van `getHourlyDistributionProfile`: Berekent de "Gouden Mal" (vliegstrategie per uur) op basis van telposten met uurs-precisie.

### 2. AI Engine (Inference & Training)
De berekeningslogica wordt verfijnd voor maximale scherpte.

#### [MODIFY] [AiInferenceEngine.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/AiInferenceEngine.kt)
- Implementatie van het **Floating Window (7/9 dagen)**.
- Implementatie van **Temporal Blueprinting**: Dag-totalen van andere posten worden automatisch "herschreven" naar uurs-data op basis van de vliegprofielen.
- Verfijning van de `fWind` factor: Van 22,5° naar **5,25° tolerantie**.
- Toevoegen van de **72-uurs Corridor Correlation** voor Birds-of-Interest.

#### [MODIFY] [Trainer.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/Trainer.kt)
- Update van de `fillScientificVault` methode om de nieuwe uurs-profielen en betrouwbaarheids-indices op te slaan.

### 3. Configuratie & Mapping
#### [MODIFY] [AiConfig.kt](file:///C:/Eigen%20bestanden%20Yves/Programeren/Android/VoiceTally/app/src/main/java/com/yvesds/vt5/ai/AiConfig.kt)
- Definities toevoegen voor de BoI drempelwaarden en de "Anchor Sites" (telposten met uurs-precisie).

## Verification Plan

### Automated Tests
- Een nieuwe unit test `BsiPrecisionTest.kt` om de wind-match logica (5.25°) te valideren.
- Verificatie van de "Temporal Blueprinting" door een dag-totaal in te voeren en te controleren of de BSI dit correct over de uren verdeelt in de berekening.

### Manual Verification
- Uitvoeren van een volledige "BSI Training" en controleren van de `AiLog` om te zien of de scores voor BoI's daadwerkelijk scherper (meer gedifferentieerd) zijn.
- Inspecteren van de `species_phenology_vault` om de nieuwe uurs-curven te valideren.
